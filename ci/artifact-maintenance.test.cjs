const { test } = require('node:test');
const assert = require('node:assert/strict');
const { createHash } = require('node:crypto');
const maintain = require('./artifact-maintenance.cjs');
const now = Date.parse('2026-11-16T12:00:00Z');

function fixture(name = 'application-apk') {
  const archive = Buffer.from('original artifact ZIP');
  const digest = `sha256:${createHash('sha256').update(archive).digest('hex')}`;
  const artifact = { id: 11, name, size_in_bytes: archive.length, digest, created_at: '2026-11-10T12:00:00Z', expired: false, workflow_run: { id: 22 } };
  const run = { id: 22, run_number: 4, status: 'completed', head_sha: 'abc123', html_url: 'https://github.com/owner/repo/actions/runs/22' };
  const state = { artifacts: [artifact], run, archive, uploads: [], deleted: [], releases: [], assets: [], failed: [], calls: [] };
  const repos = {
    getReleaseByTag: async () => {
      if (!state.releases.length) throw Object.assign(new Error('missing'), { status: 404 });
      return { data: state.releases[0] };
    },
    createRelease: async args => {
      const data = { ...args, id: 33, upload_url: 'https://uploads.github.com/33{?name,label}' };
      state.releases.push(data);
      return { data };
    },
    listReleaseAssets: 'assets',
    uploadReleaseAsset: async args => {
      state.uploads.push(args);
      const data = { id: 44, name: args.name, state: 'uploaded', size: args.data.length, digest };
      state.assets.push(data);
      return { data };
    },
    getReleaseAsset: async () => ({ data: archive })
  };
  const actions = {
    listArtifactsForRepo: 'artifacts',
    getWorkflowRun: async () => ({ data: state.run }),
    downloadArtifact: async () => ({ data: state.archive }),
    deleteArtifact: async args => { state.deleted.push(args.artifact_id); }
  };
  const github = { rest: { actions, repos }, paginate: async (endpoint, args) => { state.calls.push({ endpoint, args }); return state[endpoint]; } };
  const summary = { addHeading: () => summary, addRaw: () => summary, write: async () => {} };
  const core = { info: () => {}, warning: () => {}, setFailed: message => state.failed.push(message), summary };
  return { state, artifact, github, invoke: options => maintain({ github, context: { repo: { owner: 'owner', repo: 'repo' } }, core, now, ...options }) };
}

test('archives a download as a prerelease and verifies its bytes before deletion', async () => {
  const f = fixture();
  const result = await f.invoke();
  assert.deepEqual(f.state.deleted, [11]);
  assert.equal(result.bytes, f.state.archive.length);
  assert.equal(f.state.releases[0].prerelease, true);
  assert.equal(f.state.releases[0].make_latest, 'false');
  assert.equal(f.state.releases[0].target_commitish, 'abc123');
  assert.deepEqual(f.state.uploads[0].data, f.state.archive);
  assert.ok(f.state.calls.every(call => call.args.per_page === 100));
});

test('active, orphaned and expired artifacts are preserved', async () => {
  for (const scenario of ['active', 'orphaned', 'expired']) {
    const f = fixture();
    if (scenario === 'active') f.state.run.status = 'in_progress';
    if (scenario === 'orphaned') delete f.artifact.workflow_run;
    if (scenario === 'expired') f.artifact.expired = true;
    await f.invoke();
    assert.deepEqual(f.state.deleted, []);
    assert.deepEqual(f.state.uploads, []);
  }
});

test('only old known reports expire without archiving', async () => {
  const old = fixture('test-reports');
  await old.invoke();
  assert.deepEqual(old.state.deleted, [11]);
  assert.deepEqual(old.state.uploads, []);
  for (const date of ['2026-11-15T12:00:00Z', 'unparseable']) {
    const fresh = fixture('lint-raport');
    fresh.artifact.created_at = date;
    await fresh.invoke();
    assert.deepEqual(fresh.state.deleted, []);
  }
});

test('unknown artifact names are archived instead of discarded', async () => {
  const f = fixture('size-report-apk');
  await f.invoke();
  assert.equal(f.state.uploads.length, 1);
});

test('a failed upload cannot delete the original', async () => {
  const f = fixture();
  f.github.rest.repos.uploadReleaseAsset = async () => { throw new Error('quota or network failure'); };
  await f.invoke();
  assert.deepEqual(f.state.deleted, []);
  assert.equal(f.state.failed.length, 1);
});

test('checksums and complete release uploads are required', async () => {
  for (const scenario of ['download', 'checksum', 'size', 'state']) {
    const f = fixture();
    if (scenario === 'download') f.state.archive = Buffer.from('corrupted');
    else {
      const upload = f.github.rest.repos.uploadReleaseAsset;
      f.github.rest.repos.uploadReleaseAsset = async args => {
        const r = await upload(args);
        if (scenario === 'checksum') r.data.digest = 'sha256:wrong';
        if (scenario === 'size') r.data.size = 0;
        if (scenario === 'state') r.data.state = 'starter';
        return r;
      };
    }
    await f.invoke();
    assert.deepEqual(f.state.deleted, []);
    assert.equal(f.state.failed.length, 1);
  }
});

test('an API response without a release checksum is verified by downloading it', async () => {
  const f = fixture();
  const upload = f.github.rest.repos.uploadReleaseAsset;
  f.github.rest.repos.uploadReleaseAsset = async args => {
    const r = await upload(args);
    delete r.data.digest;
    return r;
  };
  await f.invoke();
  assert.deepEqual(f.state.deleted, [11]);
});

test('rerunning after a partial migration reuses the verified archive', async () => {
  const f = fixture();
  f.github.rest.actions.deleteArtifact = async () => { throw new Error('temporary failure'); };
  await f.invoke();
  f.github.rest.actions.deleteArtifact = async ({ artifact_id }) => f.state.deleted.push(artifact_id);
  await f.invoke();
  assert.equal(f.state.uploads.length, 1);
  assert.deepEqual(f.state.deleted, [11]);
});

test('a run restarted during migration keeps its artifacts', async () => {
  const f = fixture();
  let calls = 0;
  f.github.rest.actions.getWorkflowRun = async () => ({ data: { ...f.state.run, status: ++calls === 1 ? 'completed' : 'queued' } });
  await f.invoke();
  assert.equal(f.state.uploads.length, 1);
  assert.deepEqual(f.state.deleted, []);
});

test('dry run does not create releases or delete artifacts', async () => {
  const f = fixture();
  await f.invoke({ dryRun: true });
  assert.deepEqual(f.state.releases, []);
  assert.deepEqual(f.state.deleted, []);
});

test('oversized artifacts and unrelated archive tags are preserved', async () => {
  const large = fixture();
  large.artifact.size_in_bytes = 2 * 1024 ** 3;
  await large.invoke();
  assert.deepEqual(large.state.deleted, []);
  const collision = fixture();
  collision.state.releases.push({ id: 33, body: 'Unrelated release' });
  await collision.invoke();
  assert.deepEqual(collision.state.deleted, []);
  assert.equal(collision.state.failed.length, 1);
});

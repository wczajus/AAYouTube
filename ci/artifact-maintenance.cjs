const { createHash } = require('node:crypto');

// Release archives preserve existing downloads before freeing Actions storage.
// No workflow runs, tags, existing releases or dependency caches are deleted.
module.exports = async function maintainArtifacts({ github, context, core, now = Date.now(), dryRun = false }) {
  const repo = context.repo;
  const artifacts = await github.paginate(github.rest.actions.listArtifactsForRepo, { ...repo, per_page: 100 });
  const runs = new Map();
  const releases = new Map();
  const result = { deleted: 0, archived: 0, bytes: 0, skipped: 0, errors: 0 };
  const reportNames = /^(?:test-reports|lint-raport|lint-report|lint-reports|test-results|coverage|playwright-report)$/i;
  const getRun = async id => {
    if (!runs.has(id)) runs.set(id, (await github.rest.actions.getWorkflowRun({ ...repo, run_id: id })).data);
    return runs.get(id);
  };

  for (const artifact of artifacts) {
    try {
      const runId = artifact.workflow_run?.id;
      if (artifact.expired || !runId) { result.skipped++; continue; }
      const run = await getRun(runId);
      if (run.status !== 'completed') { result.skipped++; continue; }
      const isReport = reportNames.test(artifact.name);
      const created = Date.parse(artifact.created_at);
      if (isReport && (!Number.isFinite(created) || now - created < 3 * 86400000)) {
        result.skipped++;
        continue;
      }
      core.info(`${dryRun ? '[dry run] ' : ''}${isReport ? 'Expire report' : 'Archive then remove'}: ${artifact.id} (${artifact.name}, ${artifact.size_in_bytes} bytes)`);
      if (dryRun) { result.skipped++; continue; }

      if (!isReport) {
        if (artifact.size_in_bytes >= 2 * 1024 ** 3) throw new Error('Archive is too large for a release asset; preserved in Actions.');
        const downloaded = await github.rest.actions.downloadArtifact({ ...repo, artifact_id: artifact.id, archive_format: 'zip' });
        const archive = Buffer.from(downloaded.data);
        if (!archive.length) throw new Error('Empty artifact download; original preserved.');
        const digest = `sha256:${createHash('sha256').update(archive).digest('hex')}`;
        if (artifact.digest && artifact.digest !== digest) throw new Error('Artifact checksum mismatch; original preserved.');
        const tag = `actions-archive-${runId}`;
        let release = releases.get(tag);
        if (!release) {
          try {
            release = (await github.rest.repos.getReleaseByTag({ ...repo, tag })).data;
          } catch (error) {
            if (error.status !== 404) throw error;
            release = (await github.rest.repos.createRelease({
              ...repo, tag_name: tag, target_commitish: run.head_sha,
              name: `Archived Actions downloads — run ${run.run_number}`,
              body: `Original Actions downloads preserved before storage cleanup.\n\nWorkflow run: ${run.html_url}\nCommit: ${run.head_sha}\n\nZIP files contain the original artifact files.`,
              prerelease: true, make_latest: 'false'
            })).data;
          }
          if (release.draft || !release.body?.includes(`Workflow run: ${run.html_url}`)) {
            throw new Error('Archive tag belongs to another release; original preserved.');
          }
          releases.set(tag, release);
        }
        const name = `${artifact.id}-${artifact.name.replace(/[^A-Za-z0-9._-]/g, '_').slice(0, 100)}.zip`;
        const assets = await github.paginate(github.rest.repos.listReleaseAssets, { ...repo, release_id: release.id, per_page: 100 });
        let asset = assets.find(item => item.name === name);
        if (!asset) {
          asset = (await github.rest.repos.uploadReleaseAsset({
            ...repo, release_id: release.id, name, data: archive,
            headers: { 'content-type': 'application/zip', 'content-length': archive.length }
          })).data;
        }
        if (asset.state !== 'uploaded' || asset.size !== archive.length) {
          throw new Error('Release asset is incomplete; original preserved.');
        }
        // Verify the stored release asset, including older API responses without a digest.
        let storedDigest = asset.digest;
        if (!storedDigest) {
          const stored = await github.rest.repos.getReleaseAsset({ ...repo, asset_id: asset.id, headers: { accept: 'application/octet-stream' } });
          storedDigest = `sha256:${createHash('sha256').update(Buffer.from(stored.data)).digest('hex')}`;
        }
        if (storedDigest !== digest) throw new Error('Release checksum mismatch; original preserved.');
        result.archived++;
      }

      // A completed workflow may have been rerun while its archive was being copied.
      const current = (await github.rest.actions.getWorkflowRun({ ...repo, run_id: runId })).data;
      if (current.status !== 'completed') { result.skipped++; continue; }
      await github.rest.actions.deleteArtifact({ ...repo, artifact_id: artifact.id });
      result.deleted++;
      result.bytes += artifact.size_in_bytes;
    } catch (error) {
      if (error.status === 404 || error.status === 410) {
        // Missing downloads are not considered successfully archived or deleted.
        core.warning(`Artifact ${artifact.id} is unavailable; no deletion requested after the error.`);
        result.skipped++;
      } else {
        result.errors++;
        core.warning(`Artifact ${artifact.id} preserved: ${error.message}`);
      }
    }
  }
  await core.summary.addHeading('Actions artifact maintenance')
    .addRaw(`${dryRun ? 'Dry run. ' : ''}Archived: ${result.archived}; deleted: ${result.deleted}; freed: ${(result.bytes / 1024 ** 2).toFixed(1)} MiB; skipped: ${result.skipped}; errors: ${result.errors}.\n\n`)
    .addRaw('Downloads are copied to archive prereleases and verified with SHA-256 before deletion. Reports expire after 3 days. Active workflows, logs and dependency caches are preserved.').write();
  if (result.errors) core.setFailed(`${result.errors} artifact(s) could not be safely archived; originals were preserved.`);
  return result;
};

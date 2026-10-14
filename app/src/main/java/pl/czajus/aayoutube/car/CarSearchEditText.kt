package pl.czajus.aayoutube.car

import android.content.Context
import android.support.car.input.CarRestrictedEditText
import android.view.inputmethod.EditorInfo
import com.google.android.gms.car.input.CarEditable
import com.google.android.gms.car.input.CarEditableListener

/** Text field that types with the car's own keyboard (same approach as Fermata's CarEditText). */
class CarSearchEditText(
    context: Context,
    private val onSubmit: (String) -> Unit,
) : CarRestrictedEditText(context, null), CarEditable {

    init {
        isSingleLine = true
        imeOptions = EditorInfo.IME_ACTION_SEARCH
    }

    override fun setCarEditableListener(listener: CarEditableListener) {
        super.setCarEditableListener(
            android.support.car.input.CarEditableListener { selStart, selEnd, candStart, candEnd ->
                listener.onUpdateSelection(selStart, selEnd, candStart, candEnd)
            },
        )
    }

    override fun onEditorAction(actionCode: Int) {
        onSubmit(text?.toString().orEmpty())
    }
}

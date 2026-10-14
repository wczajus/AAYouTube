package pl.czajus.aayoutube.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ItemList
import androidx.car.app.model.SearchTemplate
import androidx.car.app.model.Template
import pl.czajus.aayoutube.R

/** Uses the car's own keyboard; returns the submitted text to [BrowserScreen]. */
class SearchScreen(carContext: CarContext) : Screen(carContext) {

    override fun onGetTemplate(): Template {
        val callback = object : SearchTemplate.SearchCallback {
            override fun onSearchTextChanged(searchText: String) = Unit

            override fun onSearchSubmitted(searchText: String) {
                setResult(searchText)
                screenManager.pop()
            }
        }
        return SearchTemplate.Builder(callback)
            .setHeaderAction(Action.BACK)
            .setSearchHint(carContext.getString(R.string.search_hint))
            .setShowKeyboardByDefault(true)
            .setItemList(
                ItemList.Builder()
                    .setNoItemsMessage(carContext.getString(R.string.search_empty))
                    .build(),
            )
            .build()
    }
}

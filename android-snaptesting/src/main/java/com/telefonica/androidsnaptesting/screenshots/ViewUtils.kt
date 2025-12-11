/*Copyright (c) 2022 Pedro Vicente Gómez Sánchez

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.*/

package com.telefonica.androidsnaptesting.screenshots

import android.view.View
import android.view.ViewGroup

@Suppress("UNCHECKED_CAST")
public inline fun <reified T : View> View.childrenViews(): List<T> = filterChildrenViews {
    it is T
} as List<T>

public fun View.filterChildrenViews(filter: (View) -> Boolean): List<View> {
    val children = mutableSetOf<View>()
    val view = this
    if (view !is ViewGroup) {
        if (filter.invoke(view)) {
            children.add(view)
        }
    } else {
        for (i in 0 until view.childCount) {
            view.getChildAt(i).let {
                children.addAll(it.filterChildrenViews(filter))
                if (filter.invoke(it)) {
                    children.add(it)
                }
            }
        }
    }

    return children.toList()
}

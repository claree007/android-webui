package com.androidwebui.android.ui.shell

/** One row of the model selector. [group] is the connection or tag the filter chip narrows by. */
data class ModelOption(val id: String, val name: String, val group: String? = null)

sealed interface ModelListState {
    data object Loading : ModelListState
    data class Error(val message: String) : ModelListState
    data class Loaded(val models: List<ModelOption>) : ModelListState
}

/** Case-insensitive match on name or id, narrowed to [group] when it is not null. */
fun List<ModelOption>.filterModels(query: String, group: String?): List<ModelOption> {
    val q = query.trim()
    return filter { m ->
        (group == null || m.group == group) &&
            (q.isEmpty() || m.name.contains(q, ignoreCase = true) || m.id.contains(q, ignoreCase = true))
    }
}

package com.nikolaspaci.app.llamallmlocal.data.huggingface

import com.google.gson.annotations.SerializedName

data class HfModel(
    @SerializedName("id") val id: String = "",
    @SerializedName("author") val author: String? = null,
    @SerializedName("downloads") val downloads: Int = 0,
    @SerializedName("tags") val tags: List<String>? = null,
    @SerializedName("siblings") val siblings: List<HfSibling>? = null
)

data class HfSibling(
    @SerializedName("rfilename") val rfilename: String = "",
    @SerializedName("size") val size: Long? = null
)

data class HfModelDetail(
    @SerializedName("id") val id: String = "",
    @SerializedName("author") val author: String? = null,
    @SerializedName("downloads") val downloads: Int = 0,
    @SerializedName("tags") val tags: List<String>? = null,
    @SerializedName("siblings") val siblings: List<HfSibling>? = null
) {
    val ggufFiles: List<HfSibling>
        get() = siblings?.filter { it.rfilename.endsWith(".gguf", ignoreCase = true) } ?: emptyList()
}

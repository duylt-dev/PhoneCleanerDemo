package com.pion.phonecleaner.domain.model.file

import kotlinx.collections.immutable.ImmutableList

data class ZipInputFile(
    val uri: String,
    val displayName: String,
    val sizeBytes: Long,
)

data class ZipFileRequest(
    val files: ImmutableList<ZipInputFile>,
    val fileName: String,
)

data class ZipFileOutcome(
    val uri: String,
    val fileName: String,
    val inputCount: Int,
    val inputBytes: Long,
    val outputBytes: Long,
)

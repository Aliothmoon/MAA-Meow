package com.aliothmoon.maameow.data.repository

import com.aliothmoon.maameow.data.model.toolbox.OperBoxOperator

/**
 * 干员识别的导出列表：owned + notOwned（全部可用干员）。
 *
 * 没识别过就返回空表，避免导出/分享出一份空数据还被当成有效结果。
 * 工具页面与库存页共用。
 */
fun OperBoxSnapshot.toExportList(): List<OperBoxOperator> =
    if (!hasSynced) emptyList() else owned + notOwned

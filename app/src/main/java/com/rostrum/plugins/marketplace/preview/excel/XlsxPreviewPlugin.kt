package com.rostrum.plugins.marketplace.preview.excel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rostrum.core.mcp.*
import com.rostrum.core.office.OfficeDocumentRenderer
import com.rostrum.core.plugin.Plugin
import com.rostrum.core.plugin.PluginCapability
import com.rostrum.core.plugin.PluginCategory
import com.rostrum.core.plugin.PluginContext
import com.rostrum.core.plugin.models.FileInfo
import com.rostrum.core.plugin.providers.FilePreviewPlugin
import com.rostrum.core.plugin.providers.PreviewMetadata
import com.rostrum.core.plugin.providers.PreviewResult
import com.rostrum.core.plugin.providers.ToolPlugin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Excel 文件预览插件
 * 
 * 使用 Apache POI 预览 Excel 文件（.xlsx, .xls）
 * 支持：单元格数据、公式、格式、多工作表
 * 
 * 协议：Apache License 2.0（可商用、可修改、无需开源）
 * 
 * 插件ID: com.rostrum.plugin.preview.excel
 * 版本: 1.0.0
 * 作者: OmniMaster Team
 * 
 * 实现 ToolPlugin 接口，提供MCP工具供AI调用
 */
class XlsxPreviewPlugin : Plugin, FilePreviewPlugin, ToolPlugin {
    
    override val id = "com.rostrum.plugin.preview.excel"
    override val name = "Excel Preview"
    override val version = "1.0.0"
    override val author = "OmniMaster Team"
    override val description = "Excel 文件预览插件，支持单元格数据、公式、格式、多工作表预览和编辑"
    override val category = PluginCategory.PREVIEW
    override val dependencies: List<String> = emptyList()
    
    override val supportedMimeTypes = listOf(
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-excel"
    )
    
    override val supportedExtensions = listOf(".xlsx", ".xls")
    
    private var pluginContext: PluginContext? = null
    
    override suspend fun initialize(context: PluginContext): Result<Unit> {
        pluginContext = context
        return Result.success(Unit)
    }
    
    override suspend fun onActivate() = Result.success(Unit)
    override suspend fun onDeactivate() = Result.success(Unit)
    override suspend fun onDestroy(): Result<Unit> {
        pluginContext = null
        return Result.success(Unit)
    }
    
    override fun getCapabilities() = listOf(PluginCapability.FILE_PREVIEW)
    
    override fun canPreview(file: FileInfo): Boolean {
        return supportedExtensions.any { file.extension.equals(it, ignoreCase = true) } ||
               file.mimeType?.contains("spreadsheetml") == true ||
               file.mimeType?.contains("ms-excel") == true
    }
    
    override suspend fun createPreview(file: FileInfo): PreviewResult {
        if (!canPreview(file)) return PreviewResult.Unsupported
        
        val fileObj = File(file.path)
        if (!fileObj.exists()) {
            return PreviewResult.Error("文件不存在: ${file.path}")
        }
        
        return try {
            // 检查文件权限
            if (!fileObj.canRead()) {
                return PreviewResult.Error("无法读取文件，请检查文件权限")
            }
            
            // 使用Apache POI读取Excel
            val excelInfo = withContext(Dispatchers.IO) {
                try {
                    OfficeDocumentRenderer.readExcel(fileObj)
                } catch (e: org.apache.poi.EmptyFileException) {
                    throw IllegalArgumentException("Excel文件为空或已损坏")
                } catch (e: org.apache.poi.openxml4j.exceptions.NotOfficeXmlFileException) {
                    throw IllegalArgumentException("不是有效的Excel文件格式")
                } catch (e: org.apache.poi.openxml4j.exceptions.OLE2NotOfficeXmlFileException) {
                    throw IllegalArgumentException("不支持旧版Excel格式(.xls)，请转换为.xlsx格式")
                } catch (e: java.util.zip.ZipException) {
                    throw IllegalArgumentException("Excel文件已损坏或格式错误")
                }
            }
            
            // 创建预览组件（带缩放功能）
            val previewComponent: @Composable () -> Unit = {
                ExcelPreviewContent(excelInfo)
            }
            
            PreviewResult.Success(
                previewComponent = previewComponent,
                metadata = PreviewMetadata(
                    title = file.name,
                    description = "Excel 表格预览（${excelInfo.sheetCount}个工作表）",
                    canEdit = true,
                    canExport = true
                )
            )
        } catch (e: SecurityException) {
            PreviewResult.Error("文件权限错误: ${e.message}", e)
        } catch (e: OutOfMemoryError) {
            PreviewResult.Error("内存不足，文件可能过大", e)
        } catch (e: Exception) {
            android.util.Log.e("XlsxPreview", "Excel预览失败", e)
            PreviewResult.Error("预览失败: ${e.message ?: e.javaClass.simpleName}", e)
        }
    }
    
    override fun getPreviewPriority(file: FileInfo) = 100
    
    // ==================== ToolPlugin 实现 ====================
    
    override val toolCategory = ToolCategory.FILE_SYSTEM
    
    override fun getMCPTools(): List<MCPTool> {
        return listOf(
            MCPTool(
                name = "excel_read_sheet",
                description = "读取Excel工作表的单元格数据",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "Excel文件路径"),
                        "sheetIndex" to JsonSchemaProperty(type = "number", description = "工作表索引（从0开始），默认0", default = 0)
                    ),
                    required = listOf("filePath")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "sheetName" to JsonSchemaProperty(type = "string", description = "工作表名称"),
                        "rows" to JsonSchemaProperty(
                            type = "array",
                            description = "行数据",
                            items = JsonSchemaProperty(
                                type = "array",
                                items = JsonSchemaProperty(type = "string")
                            )
                        ),
                        "rowCount" to JsonSchemaProperty(type = "number"),
                        "columnCount" to JsonSchemaProperty(type = "number")
                    )
                ),
                handler = { args, context ->
                    try {
                        val filePath = args["filePath"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少filePath参数")
                            )
                        
                        val sheetIndex = (args["sheetIndex"] as? Number)?.toInt() ?: 0
                        
                        val file = File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "FILE_NOT_FOUND", message = "文件不存在: $filePath")
                            )
                        }
                        
                        val sheet = withContext(Dispatchers.IO) {
                            OfficeDocumentRenderer.readExcelSheet(file, sheetIndex)
                        }
                        
                        MCPResult(
                            success = true,
                            data = mapOf(
                                "sheetName" to sheet.name,
                                "rows" to sheet.rows.map { row -> row.map { it.value } },
                                "rowCount" to sheet.rowCount,
                                "columnCount" to sheet.columnCount
                            )
                        )
                    } catch (e: Exception) {
                        MCPResult(
                            success = false,
                            error = MCPError(code = "READ_FAILED", message = "读取失败: ${e.message}")
                        )
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE)
            ),
            MCPTool(
                name = "excel_modify_cell",
                description = "修改Excel单元格的值",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "Excel文件路径"),
                        "sheetIndex" to JsonSchemaProperty(type = "number", description = "工作表索引，默认0", default = 0),
                        "rowIndex" to JsonSchemaProperty(type = "number", description = "行索引（从0开始）"),
                        "columnIndex" to JsonSchemaProperty(type = "number", description = "列索引（从0开始）"),
                        "value" to JsonSchemaProperty(type = "string", description = "单元格新值")
                    ),
                    required = listOf("filePath", "rowIndex", "columnIndex", "value")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "message" to JsonSchemaProperty(type = "string")
                    )
                ),
                handler = { args, context ->
                    try {
                        val filePath = args["filePath"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少filePath参数")
                            )
                        
                        val sheetIndex = (args["sheetIndex"] as? Number)?.toInt() ?: 0
                        val rowIndex = (args["rowIndex"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少rowIndex参数")
                            )
                        
                        val columnIndex = (args["columnIndex"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少columnIndex参数")
                            )
                        
                        val value = args["value"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少value参数")
                            )
                        
                        val file = File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "FILE_NOT_FOUND", message = "文件不存在: $filePath")
                            )
                        }
                        
                        val result = withContext(Dispatchers.IO) {
                            OfficeDocumentRenderer.modifyExcelCell(file, sheetIndex, rowIndex, columnIndex, value)
                        }
                        if (result.isSuccess) {
                            MCPResult(
                                success = true,
                                data = mapOf("message" to "修改成功")
                            )
                        } else {
                            MCPResult(
                                success = false,
                                error = MCPError(code = "MODIFICATION_FAILED", message = result.exceptionOrNull()?.message ?: "修改失败")
                            )
                        }
                    } catch (e: Exception) {
                        MCPResult(
                            success = false,
                            error = MCPError(code = "MODIFICATION_FAILED", message = "修改失败: ${e.message}")
                        )
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE, ToolPermission.WRITE_FILE)
            ),
            MCPTool(
                name = "excel_set_cell_format",
                description = "设置Excel单元格格式（数字、日期、货币、颜色、边框、对齐）",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "Excel文件路径"),
                        "sheetIndex" to JsonSchemaProperty(type = "number", description = "工作表索引，默认0", default = 0),
                        "rowIndex" to JsonSchemaProperty(type = "number", description = "行索引（从0开始）"),
                        "columnIndex" to JsonSchemaProperty(type = "number", description = "列索引（从0开始）"),
                        "dataFormat" to JsonSchemaProperty(type = "string", description = "数据格式，如 '0.00', 'yyyy-mm-dd', '\$#,##0.00'"),
                        "horizontalAlignment" to JsonSchemaProperty(
                            type = "string",
                            description = "水平对齐：LEFT, CENTER, RIGHT, JUSTIFY",
                            enum = listOf("LEFT", "CENTER", "RIGHT", "JUSTIFY")
                        ),
                        "verticalAlignment" to JsonSchemaProperty(
                            type = "string",
                            description = "垂直对齐：TOP, CENTER, BOTTOM",
                            enum = listOf("TOP", "CENTER", "BOTTOM")
                        ),
                        "borderColor" to JsonSchemaProperty(type = "string", description = "边框颜色，如 '#FF0000'"),
                        "backgroundColor" to JsonSchemaProperty(type = "string", description = "背景颜色，如 '#FFFF00'")
                    ),
                    required = listOf("filePath", "rowIndex", "columnIndex")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "message" to JsonSchemaProperty(type = "string")
                    )
                ),
                handler = { args, context ->
                    try {
                        val filePath = args["filePath"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少filePath参数")
                            )
                        
                        val sheetIndex = (args["sheetIndex"] as? Number)?.toInt() ?: 0
                        val rowIndex = (args["rowIndex"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少rowIndex参数")
                            )
                        
                        val columnIndex = (args["columnIndex"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少columnIndex参数")
                            )
                        
                        val file = File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "FILE_NOT_FOUND", message = "文件不存在: $filePath")
                            )
                        }
                        
                        val horizontalAlignment = (args["horizontalAlignment"] as? String)?.let {
                            when (it.uppercase()) {
                                "LEFT" -> org.apache.poi.ss.usermodel.HorizontalAlignment.LEFT
                                "CENTER" -> org.apache.poi.ss.usermodel.HorizontalAlignment.CENTER
                                "RIGHT" -> org.apache.poi.ss.usermodel.HorizontalAlignment.RIGHT
                                "JUSTIFY" -> org.apache.poi.ss.usermodel.HorizontalAlignment.JUSTIFY
                                else -> null
                            }
                        }
                        
                        val verticalAlignment = (args["verticalAlignment"] as? String)?.let {
                            when (it.uppercase()) {
                                "TOP" -> org.apache.poi.ss.usermodel.VerticalAlignment.TOP
                                "CENTER" -> org.apache.poi.ss.usermodel.VerticalAlignment.CENTER
                                "BOTTOM" -> org.apache.poi.ss.usermodel.VerticalAlignment.BOTTOM
                                else -> null
                            }
                        }
                        
                        val format = OfficeDocumentRenderer.ExcelCellFormat(
                            dataFormat = args["dataFormat"] as? String,
                            horizontalAlignment = horizontalAlignment,
                            verticalAlignment = verticalAlignment,
                            borderColor = args["borderColor"] as? String,
                            backgroundColor = args["backgroundColor"] as? String
                        )
                        
                        val result = withContext(Dispatchers.IO) {
                            OfficeDocumentRenderer.setExcelCellFormat(file, sheetIndex, rowIndex, columnIndex, format)
                        }
                        if (result.isSuccess) {
                            MCPResult(
                                success = true,
                                data = mapOf("message" to "设置格式成功")
                            )
                        } else {
                            MCPResult(
                                success = false,
                                error = MCPError(code = "MODIFICATION_FAILED", message = result.exceptionOrNull()?.message ?: "设置格式失败")
                            )
                        }
                    } catch (e: Exception) {
                        MCPResult(
                            success = false,
                            error = MCPError(code = "MODIFICATION_FAILED", message = "设置格式失败: ${e.message}")
                        )
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE, ToolPermission.WRITE_FILE)
            ),
            MCPTool(
                name = "excel_set_formula",
                description = "设置Excel单元格公式",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "Excel文件路径"),
                        "sheetIndex" to JsonSchemaProperty(type = "number", description = "工作表索引，默认0", default = 0),
                        "rowIndex" to JsonSchemaProperty(type = "number", description = "行索引（从0开始）"),
                        "columnIndex" to JsonSchemaProperty(type = "number", description = "列索引（从0开始）"),
                        "formula" to JsonSchemaProperty(type = "string", description = "公式，如 'SUM(A1:A10)', '=A1+B1'")
                    ),
                    required = listOf("filePath", "rowIndex", "columnIndex", "formula")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "message" to JsonSchemaProperty(type = "string")
                    )
                ),
                handler = { args, context ->
                    try {
                        val filePath = args["filePath"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少filePath参数")
                            )
                        
                        val sheetIndex = (args["sheetIndex"] as? Number)?.toInt() ?: 0
                        val rowIndex = (args["rowIndex"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少rowIndex参数")
                            )
                        
                        val columnIndex = (args["columnIndex"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少columnIndex参数")
                            )
                        
                        val formula = args["formula"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少formula参数")
                            )
                        
                        val file = File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "FILE_NOT_FOUND", message = "文件不存在: $filePath")
                            )
                        }
                        
                        // 移除公式开头的 = 号（如果存在）
                        val cleanFormula = formula.removePrefix("=")
                        val result = withContext(Dispatchers.IO) {
                            OfficeDocumentRenderer.modifyExcelCell(
                                file, sheetIndex, rowIndex, columnIndex, cleanFormula, org.apache.poi.ss.usermodel.CellType.FORMULA
                            )
                        }
                        if (result.isSuccess) {
                            MCPResult(
                                success = true,
                                data = mapOf("message" to "设置公式成功")
                            )
                        } else {
                            MCPResult(
                                success = false,
                                error = MCPError(code = "MODIFICATION_FAILED", message = result.exceptionOrNull()?.message ?: "设置公式失败")
                            )
                        }
                    } catch (e: Exception) {
                        MCPResult(
                            success = false,
                            error = MCPError(code = "MODIFICATION_FAILED", message = "设置公式失败: ${e.message}")
                        )
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE, ToolPermission.WRITE_FILE)
            ),
            MCPTool(
                name = "excel_merge_cells",
                description = "合并Excel单元格",
                inputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "filePath" to JsonSchemaProperty(type = "string", description = "Excel文件路径"),
                        "sheetIndex" to JsonSchemaProperty(type = "number", description = "工作表索引，默认0", default = 0),
                        "firstRow" to JsonSchemaProperty(type = "number", description = "起始行索引（从0开始）"),
                        "lastRow" to JsonSchemaProperty(type = "number", description = "结束行索引（从0开始）"),
                        "firstCol" to JsonSchemaProperty(type = "number", description = "起始列索引（从0开始）"),
                        "lastCol" to JsonSchemaProperty(type = "number", description = "结束列索引（从0开始）")
                    ),
                    required = listOf("filePath", "firstRow", "lastRow", "firstCol", "lastCol")
                ),
                outputSchema = JsonSchema(
                    type = "object",
                    properties = mapOf(
                        "success" to JsonSchemaProperty(type = "boolean"),
                        "message" to JsonSchemaProperty(type = "string")
                    )
                ),
                handler = { args, context ->
                    try {
                        val filePath = args["filePath"] as? String
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少filePath参数")
                            )
                        
                        val sheetIndex = (args["sheetIndex"] as? Number)?.toInt() ?: 0
                        val firstRow = (args["firstRow"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少firstRow参数")
                            )
                        
                        val lastRow = (args["lastRow"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少lastRow参数")
                            )
                        
                        val firstCol = (args["firstCol"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少firstCol参数")
                            )
                        
                        val lastCol = (args["lastCol"] as? Number)?.toInt()
                            ?: return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "INVALID_ARGUMENT", message = "缺少lastCol参数")
                            )
                        
                        val file = File(filePath)
                        if (!file.exists()) {
                            return@MCPTool MCPResult(
                                success = false,
                                error = MCPError(code = "FILE_NOT_FOUND", message = "文件不存在: $filePath")
                            )
                        }
                        
                        val result = withContext(Dispatchers.IO) {
                            OfficeDocumentRenderer.mergeExcelCells(file, sheetIndex, firstRow, lastRow, firstCol, lastCol)
                        }
                        if (result.isSuccess) {
                            MCPResult(
                                success = true,
                                data = mapOf("message" to "合并单元格成功")
                            )
                        } else {
                            MCPResult(
                                success = false,
                                error = MCPError(code = "MODIFICATION_FAILED", message = result.exceptionOrNull()?.message ?: "合并单元格失败")
                            )
                        }
                    } catch (e: Exception) {
                        MCPResult(
                            success = false,
                            error = MCPError(code = "MODIFICATION_FAILED", message = "合并单元格失败: ${e.message}")
                        )
                    }
                },
                category = ToolCategory.FILE_SYSTEM,
                permissions = listOf(ToolPermission.READ_FILE, ToolPermission.WRITE_FILE)
            )
        )
    }
}

// Excel 主题颜色
private val ExcelGreen = Color(0xFF217346)
private val ExcelGreenLight = Color(0xFF33A365)
private val ExcelHeaderBg = Color(0xFF217346)
private val ExcelHeaderText = Color.White
private val ExcelRowEven = Color(0xFFFFFFFF)
private val ExcelRowOdd = Color(0xFFF2F8F5)
private val ExcelBorder = Color(0xFFD9E5DF)
private val ExcelFormulaColor = Color(0xFF0066CC)

/**
 * Excel 预览内容组件（美化版）
 */
@Composable
private fun ExcelPreviewContent(excelInfo: OfficeDocumentRenderer.ExcelInfo) {
    var selectedSheetIndex by remember { mutableIntStateOf(0) }
    var scale by remember { mutableFloatStateOf(1.0f) }
    val horizontalScrollState = rememberScrollState()
    
    Column(modifier = Modifier.fillMaxSize()) {
        // 工具栏 - 固定高度，支持水平滚动
        Surface(
            modifier = Modifier.fillMaxWidth().height(36.dp),
            color = ExcelGreen,
            tonalElevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 左侧：图标和标题
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Filled.GridOn,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Column {
                        Text(
                            text = "Excel 预览",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "${excelInfo.sheetCount} 个工作表",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }
                
                // 右侧：缩放控制
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { scale = (scale - 0.1f).coerceAtLeast(0.5f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.ZoomOut,
                            contentDescription = "缩小",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    Surface(
                        color = Color.White.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "${(scale * 100).toInt()}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = { scale = (scale + 0.1f).coerceAtMost(2.0f) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.ZoomIn,
                            contentDescription = "放大",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    IconButton(
                        onClick = { scale = 1.0f },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.FitScreen,
                            contentDescription = "重置",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
        
        // 工作表选择器
        if (excelInfo.sheetCount > 1) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    excelInfo.sheets.forEachIndexed { index, sheet ->
                        FilterChip(
                            selected = selectedSheetIndex == index,
                            onClick = { selectedSheetIndex = index },
                            label = { 
                                Text(
                                    sheet.name,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                            },
                            leadingIcon = if (selectedSheetIndex == index) {
                                {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = ExcelGreen,
                                selectedLabelColor = Color.White,
                                selectedLeadingIconColor = Color.White
                            )
                        )
                    }
                }
            }
        }
        
        // 表格内容
        val selectedSheet = excelInfo.sheets.getOrNull(selectedSheetIndex)
        if (selectedSheet != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFFF5F5F5))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp)
                ) {
                    // 表格信息
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedSheet.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = ExcelGreen
                        )
                        Surface(
                            color = ExcelGreen.copy(alpha = 0.1f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(
                                text = "${selectedSheet.rowCount} 行 × ${selectedSheet.columnCount} 列",
                                style = MaterialTheme.typography.labelSmall,
                                color = ExcelGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    
                    // 表格卡片
                    Card(
                        modifier = Modifier.fillMaxSize(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        // 横向滚动容器
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .horizontalScroll(horizontalScrollState)
                        ) {
                            Column {
                                // 列标题行（A, B, C, ...）
                                if (selectedSheet.columnCount > 0) {
                                    Row(
                                        modifier = Modifier
                                            .background(Color(0xFFE8E8E8))
                                            .border(width = 1.dp, color = ExcelBorder)
                                    ) {
                                        // 行号列头
                                        Box(
                                            modifier = Modifier
                                                .width((40 * scale).dp)
                                                .height((28 * scale).dp)
                                                .background(Color(0xFFE0E0E0))
                                                .border(width = 0.5.dp, color = ExcelBorder),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            // 空白
                                        }
                                        
                                        // 列标题
                                        repeat(selectedSheet.columnCount) { colIndex ->
                                            Box(
                                                modifier = Modifier
                                                    .width((100 * scale).dp)
                                                    .height((28 * scale).dp)
                                                    .background(Color(0xFFE0E0E0))
                                                    .border(width = 0.5.dp, color = ExcelBorder),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = getColumnName(colIndex),
                                                    fontSize = (12 * scale).sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = Color(0xFF444444)
                                                )
                                            }
                                        }
                                    }
                                }
                                
                                // 表头（第一行数据）
                                if (selectedSheet.rows.isNotEmpty()) {
                                    Row(
                                        modifier = Modifier
                                            .background(ExcelHeaderBg)
                                            .border(width = 0.5.dp, color = ExcelBorder)
                                    ) {
                                        // 行号
                                        Box(
                                            modifier = Modifier
                                                .width((40 * scale).dp)
                                                .height((36 * scale).dp)
                                                .background(Color(0xFFE0E0E0))
                                                .border(width = 0.5.dp, color = ExcelBorder),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "1",
                                                fontSize = (11 * scale).sp,
                                                color = Color(0xFF666666)
                                            )
                                        }
                                        
                                        // 数据单元格
                                        selectedSheet.rows[0].forEachIndexed { colIndex, cell ->
                                            ExcelHeaderCell(
                                                value = cell.value.ifEmpty { "列${colIndex + 1}" },
                                                scale = scale
                                            )
                                        }
                                        
                                        // 填充空列
                                        val emptyCols = selectedSheet.columnCount - selectedSheet.rows[0].size
                                        repeat(emptyCols.coerceAtLeast(0)) { colIndex ->
                                            ExcelHeaderCell(
                                                value = "",
                                                scale = scale
                                            )
                                        }
                                    }
                                }
                                
                                // 数据行
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    itemsIndexed(selectedSheet.rows.drop(1)) { rowIndex, row ->
                                        Row(
                                            modifier = Modifier
                                                .background(
                                                    if (rowIndex % 2 == 0) ExcelRowEven else ExcelRowOdd
                                                )
                                        ) {
                                            // 行号
                                            Box(
                                                modifier = Modifier
                                                    .width((40 * scale).dp)
                                                    .height((32 * scale).dp)
                                                    .background(Color(0xFFE8E8E8))
                                                    .border(width = 0.5.dp, color = ExcelBorder),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = "${rowIndex + 2}",
                                                    fontSize = (11 * scale).sp,
                                                    color = Color(0xFF666666)
                                                )
                                            }
                                            
                                            // 数据单元格
                                            row.forEachIndexed { colIndex, cell ->
                                                ExcelDataCell(
                                                    cell = cell,
                                                    scale = scale
                                                )
                                            }
                                            
                                            // 填充空列
                                            val emptyCols = selectedSheet.columnCount - row.size
                                            repeat(emptyCols.coerceAtLeast(0)) {
                                                ExcelEmptyCell(scale = scale)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // 空状态
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "工作表为空",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 获取列名（A, B, C, ..., Z, AA, AB, ...）
 */
private fun getColumnName(index: Int): String {
    val sb = StringBuilder()
    var i = index
    while (i >= 0) {
        sb.insert(0, ('A' + (i % 26)))
        i = i / 26 - 1
    }
    return sb.toString()
}

/**
 * Excel 表头单元格
 */
@Composable
private fun ExcelHeaderCell(
    value: String,
    scale: Float
) {
    Box(
        modifier = Modifier
            .width((100 * scale).dp)
            .height((36 * scale).dp)
            .background(ExcelHeaderBg)
            .border(width = 0.5.dp, color = ExcelBorder.copy(alpha = 0.5f))
            .padding(horizontal = (6 * scale).dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = value,
            fontSize = (12 * scale).sp,
            fontWeight = FontWeight.Bold,
            color = ExcelHeaderText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Excel 数据单元格
 */
@Composable
private fun ExcelDataCell(
    cell: OfficeDocumentRenderer.ExcelCell,
    scale: Float
) {
    Column(
        modifier = Modifier
            .width((100 * scale).dp)
            .height((32 * scale).dp)
            .border(width = 0.5.dp, color = ExcelBorder)
            .padding(horizontal = (6 * scale).dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = cell.value.ifEmpty { "" },
            fontSize = (12 * scale).sp,
            fontFamily = FontFamily.Default,
            color = Color(0xFF333333),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        
        // 显示公式（如果有）
        if (cell.formula != null) {
            Text(
                text = "=${cell.formula}",
                fontSize = (9 * scale).sp,
                color = ExcelFormulaColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Excel 空单元格
 */
@Composable
private fun ExcelEmptyCell(scale: Float) {
    Box(
        modifier = Modifier
            .width((100 * scale).dp)
            .height((32 * scale).dp)
            .border(width = 0.5.dp, color = ExcelBorder)
    )
}

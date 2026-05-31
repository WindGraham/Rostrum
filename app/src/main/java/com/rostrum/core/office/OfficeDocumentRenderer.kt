package com.rostrum.core.office

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.poi.xwpf.usermodel.*
import org.apache.poi.xssf.usermodel.*
import org.apache.poi.xslf.usermodel.*
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Office文档渲染器
 * 
 * 使用 Apache POI 库处理 Office 文档
 * 支持：DOCX, XLSX, PPTX
 * 
 * 协议：Apache License 2.0（可商用、可修改、无需开源）
 * 
 * @author OmniMaster
 */
object OfficeDocumentRenderer {
    
    private const val TAG = "OfficeDocumentRenderer"
    
    // ==================== DOCX 处理 ====================
    
    /**
     * DOCX文档信息
     */
    data class DocxInfo(
        val paragraphs: List<DocxParagraph>,
        val tables: List<DocxTable>,
        val images: List<DocxImage>,
        val pageCount: Int = 1 // DOCX不直接支持页数，需要估算
    )
    
    data class DocxParagraph(
        val text: String,
        val isBold: Boolean = false,
        val isItalic: Boolean = false,
        val fontSize: Int? = null,
        val color: String? = null,
        val alignment: String? = null
    )
    
    data class DocxTable(
        val rows: List<List<String>>,
        val rowCount: Int,
        val columnCount: Int
    )
    
    data class DocxImage(
        val index: Int,
        val width: Int? = null,
        val height: Int? = null
    )
    
    /**
     * 读取DOCX文档内容
     */
    suspend fun readDocx(file: File): DocxInfo = withContext(Dispatchers.IO) {
        FileInputStream(file).use { fis ->
            XWPFDocument(fis).use { document ->
                @Suppress("DEPRECATION")
                val paragraphs = document.paragraphs.map { para ->
                    val firstRun = para.runs.firstOrNull()
                    DocxParagraph(
                        text = para.text,
                        isBold = para.runs.any { it.isBold },
                        isItalic = para.runs.any { it.isItalic },
                        fontSize = firstRun?.fontSize, // fontSize getter 已弃用，但暂时仍可使用
                        color = firstRun?.color?.let { 
                            if (it.startsWith("#")) it else "#$it"
                        },
                        alignment = para.alignment?.name
                    )
                }
                
                val tables = document.tables.map { table ->
                    val tableRows = table.rows.map { tableRow ->
                        tableRow.tableCells.map { cell -> cell.text }
                    }
                    val firstRow = if (table.numberOfRows > 0) table.getRow(0) else null
                    DocxTable(
                        rows = tableRows,
                        rowCount = table.numberOfRows,
                        columnCount = firstRow?.tableCells?.size ?: 0
                    )
                }
                
                // 提取图片（pictures 是 protected，需要通过其他方式获取）
                val images = try {
                    // 尝试通过反射获取图片列表
                    val picturesField = XWPFDocument::class.java.getDeclaredField("pictures")
                    picturesField.isAccessible = true
                    @Suppress("UNCHECKED_CAST")
                    val picturesList = picturesField.get(document) as? List<*> ?: emptyList<Any>()
                    picturesList.mapIndexed { index, _ ->
                        DocxImage(
                            index = index,
                            width = null,
                            height = null
                        )
                    }
                } catch (e: Exception) {
                    // 如果反射失败，返回空列表
                    emptyList()
                }
                
                DocxInfo(
                    paragraphs = paragraphs,
                    tables = tables,
                    images = images
                )
            }
        }
    }
    
    /**
     * 提取DOCX纯文本
     */
    suspend fun extractDocxText(file: File): String = withContext(Dispatchers.IO) {
        FileInputStream(file).use { fis ->
            XWPFDocument(fis).use { document ->
                document.paragraphs.joinToString("\n") { it.text }
            }
        }
    }
    
    /**
     * 创建新的DOCX文档
     */
    suspend fun createDocx(file: File, content: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            XWPFDocument().use { document ->
                val paragraph = document.createParagraph()
                paragraph.createRun().setText(content)
                
                FileOutputStream(file).use { fos ->
                    document.write(fos)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "创建DOCX失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 修改DOCX文档
     */
    suspend fun modifyDocx(
        file: File,
        modifications: List<DocxModification>
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            FileInputStream(file).use { fis ->
                XWPFDocument(fis).use { document ->
                    modifications.forEach { mod ->
                        when (mod) {
                            is DocxModification.AddParagraph -> {
                                val para = document.createParagraph()
                                val run = para.createRun()
                                run.setText(mod.text)
                                mod.bold?.let { run.isBold = it }
                                mod.italic?.let { run.isItalic = it }
                                mod.color?.let { 
                                    try {
                                        run.color = it.removePrefix("#")
                                    } catch (e: Exception) {
                                        Log.w(TAG, "设置颜色失败: $it", e)
                                    }
                                }
                                mod.fontSize?.let { run.fontSize = it }
                                mod.alignment?.let { para.alignment = it }
                            }
                            is DocxModification.ModifyParagraph -> {
                                if (mod.index < document.paragraphs.size) {
                                    val para = document.paragraphs[mod.index]
                                    if (mod.text != null) {
                                        if (para.runs.isNotEmpty()) {
                                            para.runs[0].setText(mod.text)
                                        } else {
                                            para.createRun().setText(mod.text)
                                        }
                                    }
                                    para.runs.forEach { run ->
                                        mod.bold?.let { run.isBold = it }
                                        mod.italic?.let { run.isItalic = it }
                                        mod.color?.let { 
                                            try {
                                                run.color = it.removePrefix("#")
                                            } catch (e: Exception) {
                                                Log.w(TAG, "设置颜色失败: $it", e)
                                            }
                                        }
                                        mod.fontSize?.let { run.fontSize = it }
                                    }
                                    mod.alignment?.let { para.alignment = it }
                                }
                            }
                            is DocxModification.DeleteParagraph -> {
                                if (mod.index < document.paragraphs.size) {
                                    document.removeBodyElement(mod.index)
                                }
                            }
                            is DocxModification.AddTable -> {
                                val table = document.createTable(mod.rowCount, mod.columnCount)
                                mod.rows.forEachIndexed { rowIdx, row ->
                                    if (rowIdx < table.numberOfRows) {
                                        val tableRow = table.getRow(rowIdx)
                                        row.forEachIndexed { colIdx, cellValue ->
                                            if (colIdx < tableRow.tableCells.size) {
                                                tableRow.getCell(colIdx).setText(cellValue)
                                            }
                                        }
                                    }
                                }
                            }
                            is DocxModification.ModifyTable -> {
                                if (mod.tableIndex < document.tables.size) {
                                    val table = document.tables[mod.tableIndex]
                                    if (mod.rowIndex < table.numberOfRows) {
                                        val row = table.getRow(mod.rowIndex)
                                        if (mod.columnIndex < row.tableCells.size) {
                                            row.getCell(mod.columnIndex).setText(mod.value)
                                        }
                                    }
                                }
                            }
                            is DocxModification.DeleteTable -> {
                                // 注意：POI 不直接支持删除表格，需要特殊处理
                                Log.w(TAG, "删除表格功能需要特殊处理，当前版本暂不支持")
                            }
                            is DocxModification.InsertImage -> {
                                try {
                                    val imageFile = File(mod.imagePath)
                                    if (imageFile.exists()) {
                                        val para = document.createParagraph()
                                        val run = para.createRun()
                                        val imageStream = FileInputStream(imageFile)
                                        val imageType = when (imageFile.extension.lowercase()) {
                                            "png" -> XWPFDocument.PICTURE_TYPE_PNG
                                            "jpg", "jpeg" -> XWPFDocument.PICTURE_TYPE_JPEG
                                            "gif" -> XWPFDocument.PICTURE_TYPE_GIF
                                            else -> XWPFDocument.PICTURE_TYPE_JPEG
                                        }
                                        run.addPicture(
                                            imageStream,
                                            imageType,
                                            imageFile.name,
                                            mod.width ?: 200,
                                            mod.height ?: 200
                                        )
                                        imageStream.close()
                                    }
                                } catch (e: Exception) {
                                    Log.e(TAG, "插入图片失败", e)
                                }
                            }
                        }
                    }
                    
                    FileOutputStream(file).use { fos ->
                        document.write(fos)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "修改DOCX失败", e)
            Result.failure(e)
        }
    }
    
    sealed class DocxModification {
        data class AddParagraph(
            val text: String,
            val bold: Boolean? = null,
            val italic: Boolean? = null,
            val color: String? = null,
            val fontSize: Int? = null,
            val alignment: ParagraphAlignment? = null
        ) : DocxModification()
        
        data class ModifyParagraph(
            val index: Int,
            val text: String? = null,
            val bold: Boolean? = null,
            val italic: Boolean? = null,
            val color: String? = null,
            val fontSize: Int? = null,
            val alignment: ParagraphAlignment? = null
        ) : DocxModification()
        
        data class DeleteParagraph(val index: Int) : DocxModification()
        
        data class AddTable(
            val rows: List<List<String>>,
            val rowCount: Int,
            val columnCount: Int
        ) : DocxModification()
        
        data class ModifyTable(
            val tableIndex: Int,
            val rowIndex: Int,
            val columnIndex: Int,
            val value: String
        ) : DocxModification()
        
        data class DeleteTable(val index: Int) : DocxModification()
        
        data class InsertImage(
            val imagePath: String,
            val width: Int? = null,
            val height: Int? = null
        ) : DocxModification()
    }
    
    // ==================== Excel 处理 ====================
    
    /**
     * Excel工作簿信息
     */
    data class ExcelInfo(
        val sheets: List<ExcelSheet>,
        val sheetCount: Int
    )
    
    data class ExcelSheet(
        val name: String,
        val index: Int,
        val rows: List<List<ExcelCell>>,
        val rowCount: Int,
        val columnCount: Int
    )
    
    data class ExcelCell(
        val value: String,
        val type: CellType = CellType.STRING,
        val formula: String? = null,
        val numberValue: Double? = null
    )
    
    /**
     * 读取Excel工作簿
     */
    suspend fun readExcel(file: File): ExcelInfo = withContext(Dispatchers.IO) {
        FileInputStream(file).use { fis ->
            XSSFWorkbook(fis).use { workbook ->
                val sheets = (0 until workbook.numberOfSheets).map { index ->
                    val sheet = workbook.getSheetAt(index)
                    val rows = mutableListOf<List<ExcelCell>>()
                    
                    for (row in sheet) {
                        val cells = mutableListOf<ExcelCell>()
                        for (cell in row) {
                            val cellValue = when (cell.cellType) {
                                CellType.NUMERIC -> {
                                    if (DateUtil.isCellDateFormatted(cell)) {
                                        cell.dateCellValue.toString()
                                    } else {
                                        cell.numericCellValue.toString()
                                    }
                                }
                                CellType.STRING -> cell.stringCellValue
                                CellType.FORMULA -> {
                                    try {
                                        cell.numericCellValue.toString()
                                    } catch (e: Exception) {
                                        cell.stringCellValue
                                    }
                                }
                                CellType.BOOLEAN -> cell.booleanCellValue.toString()
                                CellType.BLANK -> ""
                                else -> cell.toString()
                            }
                            
                            cells.add(
                                ExcelCell(
                                    value = cellValue,
                                    type = cell.cellType,
                                    formula = if (cell.cellType == CellType.FORMULA) cell.cellFormula else null,
                                    numberValue = if (cell.cellType == CellType.NUMERIC) cell.numericCellValue else null
                                )
                            )
                        }
                        rows.add(cells)
                    }
                    
                    ExcelSheet(
                        name = sheet.sheetName,
                        index = index,
                        rows = rows,
                        rowCount = sheet.lastRowNum + 1,
                        columnCount = rows.maxOfOrNull { it.size } ?: 0
                    )
                }
                
                ExcelInfo(
                    sheets = sheets,
                    sheetCount = workbook.numberOfSheets
                )
            }
        }
    }
    
    /**
     * 读取Excel指定工作表
     */
    suspend fun readExcelSheet(file: File, sheetIndex: Int = 0): ExcelSheet = withContext(Dispatchers.IO) {
        FileInputStream(file).use { fis ->
            XSSFWorkbook(fis).use { workbook ->
                val sheet = workbook.getSheetAt(sheetIndex)
                val rows = mutableListOf<List<ExcelCell>>()
                
                for (row in sheet) {
                    val cells = mutableListOf<ExcelCell>()
                    for (cell in row) {
                        cells.add(
                            ExcelCell(
                                value = cell.toString(),
                                type = cell.cellType,
                                formula = if (cell.cellType == CellType.FORMULA) cell.cellFormula else null
                            )
                        )
                    }
                    rows.add(cells)
                }
                
                ExcelSheet(
                    name = sheet.sheetName,
                    index = sheetIndex,
                    rows = rows,
                    rowCount = sheet.lastRowNum + 1,
                    columnCount = rows.maxOfOrNull { it.size } ?: 0
                )
            }
        }
    }
    
    /**
     * 修改Excel单元格
     */
    suspend fun modifyExcelCell(
        file: File,
        sheetIndex: Int,
        rowIndex: Int,
        columnIndex: Int,
        value: String,
        cellType: CellType? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            FileInputStream(file).use { fis ->
                XSSFWorkbook(fis).use { workbook ->
                    val sheet = workbook.getSheetAt(sheetIndex)
                    var row = sheet.getRow(rowIndex)
                    if (row == null) {
                        row = sheet.createRow(rowIndex)
                    }
                    var cell = row.getCell(columnIndex)
                    if (cell == null) {
                        cell = row.createCell(columnIndex)
                    }
                    
                    when (cellType ?: CellType.STRING) {
                        CellType.NUMERIC -> {
                            try {
                                cell.setCellValue(value.toDouble())
                            } catch (e: Exception) {
                                cell.setCellValue(value)
                            }
                        }
                        CellType.FORMULA -> {
                            cell.setCellFormula(value)
                        }
                        CellType.BOOLEAN -> {
                            cell.setCellValue(value.toBoolean())
                        }
                        else -> cell.setCellValue(value)
                    }
                    
                    FileOutputStream(file).use { fos ->
                        workbook.write(fos)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "修改Excel单元格失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 设置Excel单元格格式
     */
    suspend fun setExcelCellFormat(
        file: File,
        sheetIndex: Int,
        rowIndex: Int,
        columnIndex: Int,
        format: ExcelCellFormat
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            FileInputStream(file).use { fis ->
                XSSFWorkbook(fis).use { workbook ->
                    val sheet = workbook.getSheetAt(sheetIndex)
                    var row = sheet.getRow(rowIndex)
                    if (row == null) {
                        row = sheet.createRow(rowIndex)
                    }
                    var cell = row.getCell(columnIndex)
                    if (cell == null) {
                        cell = row.createCell(columnIndex)
                    }
                    
                    val cellStyle = workbook.createCellStyle()
                    
                    // 设置数据格式
                    format.dataFormat?.let {
                        val dataFormat = workbook.createDataFormat()
                        cellStyle.dataFormat = dataFormat.getFormat(it)
                    }
                    
                    // 设置对齐方式
                    format.horizontalAlignment?.let {
                        cellStyle.alignment = it
                    }
                    format.verticalAlignment?.let {
                        cellStyle.verticalAlignment = it
                    }
                    
                    // 设置边框
                    format.borderColor?.let { colorStr ->
                        val borderStyle = format.borderStyle ?: BorderStyle.THIN
                        cellStyle.setBorderTop(borderStyle)
                        cellStyle.setBorderBottom(borderStyle)
                        cellStyle.setBorderLeft(borderStyle)
                        cellStyle.setBorderRight(borderStyle)
                        try {
                            val colorHex = colorStr.removePrefix("#")
                            // 解析 RGB 值
                            val r = colorHex.substring(0, 2).toInt(16)
                            val g = colorHex.substring(2, 4).toInt(16)
                            val b = colorHex.substring(4, 6).toInt(16)
                            val rgb = byteArrayOf(r.toByte(), g.toByte(), b.toByte())
                            val xssfColor = XSSFColor(rgb, null)
                            // cellStyle 已经是 XSSFCellStyle 类型（通过 workbook.createCellStyle() 创建）
                            (cellStyle as? XSSFCellStyle)?.let { xssfCellStyle ->
                                xssfCellStyle.setTopBorderColor(xssfColor)
                                xssfCellStyle.setBottomBorderColor(xssfColor)
                                xssfCellStyle.setLeftBorderColor(xssfColor)
                                xssfCellStyle.setRightBorderColor(xssfColor)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "设置边框颜色失败: $colorStr", e)
                        }
                    }
                    
                    // 设置背景色
                    format.backgroundColor?.let { colorStr ->
                        val fillPattern = format.fillPattern ?: FillPatternType.SOLID_FOREGROUND
                        cellStyle.fillPattern = fillPattern
                        try {
                            val colorHex = colorStr.removePrefix("#")
                            // 解析 RGB 值
                            val r = colorHex.substring(0, 2).toInt(16)
                            val g = colorHex.substring(2, 4).toInt(16)
                            val b = colorHex.substring(4, 6).toInt(16)
                            val rgb = byteArrayOf(r.toByte(), g.toByte(), b.toByte())
                            val xssfColor = XSSFColor(rgb, null)
                            (cellStyle as XSSFCellStyle).setFillForegroundColor(xssfColor)
                        } catch (e: Exception) {
                            Log.w(TAG, "设置背景颜色失败: $colorStr", e)
                        }
                    }
                    
                    cell.cellStyle = cellStyle
                    
                    FileOutputStream(file).use { fos ->
                        workbook.write(fos)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "设置Excel单元格格式失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 合并Excel单元格
     */
    suspend fun mergeExcelCells(
        file: File,
        sheetIndex: Int,
        firstRow: Int,
        lastRow: Int,
        firstCol: Int,
        lastCol: Int
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            FileInputStream(file).use { fis ->
                XSSFWorkbook(fis).use { workbook ->
                    val sheet = workbook.getSheetAt(sheetIndex)
                    sheet.addMergedRegion(
                        CellRangeAddress(
                            firstRow, lastRow, firstCol, lastCol
                        )
                    )
                    
                    FileOutputStream(file).use { fos ->
                        workbook.write(fos)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "合并Excel单元格失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * Excel单元格格式配置
     */
    data class ExcelCellFormat(
        val dataFormat: String? = null, // 如 "0.00", "yyyy-mm-dd", "$#,##0.00"
        val horizontalAlignment: HorizontalAlignment? = null,
        val verticalAlignment: VerticalAlignment? = null,
        val borderColor: String? = null, // RGB颜色，格式如 "#FF0000" 或 "FF0000"
        val borderStyle: BorderStyle? = null,
        val backgroundColor: String? = null, // RGB颜色，格式如 "#FF0000" 或 "FF0000"
        val fillPattern: FillPatternType? = null
    )
    
    // ==================== PPTX 处理 ====================
    
    /**
     * PPTX演示文稿信息
     */
    data class PptxInfo(
        val slides: List<PptxSlide>,
        val slideCount: Int
    )
    
    data class PptxSlide(
        val index: Int,
        val title: String?,
        val texts: List<String>,
        val images: List<PptxImage>
    )
    
    data class PptxImage(
        val index: Int,
        val width: Int? = null,
        val height: Int? = null,
        val data: ByteArray? = null,  // 图片数据
        val contentType: String? = null  // MIME类型
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as PptxImage
            return index == other.index
        }
        
        override fun hashCode(): Int = index
    }
    
    /**
     * 读取PPTX演示文稿
     */
    suspend fun readPptx(file: File): PptxInfo = withContext(Dispatchers.IO) {
        FileInputStream(file).use { fis ->
            XMLSlideShow(fis).use { slideShow ->
                val slides = slideShow.slides.mapIndexed { index, slide ->
                    val texts = mutableListOf<String>()
                    val images = mutableListOf<PptxImage>()
                    
                    var imageIndex = 0
                    
                    // 提取文本和图片
                    slide.shapes.forEach { shape ->
                        when (shape) {
                            is XSLFTextShape -> {
                                shape.textParagraphs.forEach { para ->
                                    para.textRuns.forEach { run ->
                                        val text = run.rawText
                                        if (text.isNotBlank()) {
                                            texts.add(text)
                                        }
                                    }
                                }
                            }
                            is XSLFPictureShape -> {
                                try {
                                    val pictureData = shape.pictureData
                                    if (pictureData != null) {
                                        // 尝试获取图片尺寸（Android上不支持java.awt，所以不获取anchor）
                                        images.add(
                                            PptxImage(
                                                index = imageIndex++,
                                                width = null,  // Android不支持java.awt
                                                height = null,
                                                data = pictureData.data,
                                                contentType = pictureData.contentType
                                            )
                                        )
                                    }
                                } catch (e: Exception) {
                                    Log.w(TAG, "提取图片失败: ${e.message}")
                                    images.add(
                                        PptxImage(
                                            index = imageIndex++,
                                            width = null,
                                            height = null,
                                            data = null,
                                            contentType = null
                                        )
                                    )
                                }
                            }
                        }
                    }
                    
                    PptxSlide(
                        index = index,
                        title = texts.firstOrNull(),
                        texts = texts,
                        images = images
                    )
                }
                
                PptxInfo(
                    slides = slides,
                    slideCount = slideShow.slides.size
                )
            }
        }
    }
    
    /**
     * 提取PPTX文本内容
     */
    suspend fun extractPptxText(file: File): String = withContext(Dispatchers.IO) {
        FileInputStream(file).use { fis ->
            XMLSlideShow(fis).use { slideShow ->
                slideShow.slides.mapIndexed { index, slide ->
                    val texts = mutableListOf<String>()
                    slide.shapes.forEach { shape ->
                        if (shape is XSLFTextShape) {
                            shape.textParagraphs.forEach { para ->
                                para.textRuns.forEach { run ->
                                    texts.add(run.rawText)
                                }
                            }
                        }
                    }
                    "幻灯片 ${index + 1}:\n${texts.joinToString("\n")}"
                }.joinToString("\n\n")
            }
        }
    }
    
    /**
     * 修改PPTX幻灯片文本
     */
    suspend fun modifyPptxSlideText(
        file: File,
        slideIndex: Int,
        shapeIndex: Int,
        text: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            FileInputStream(file).use { fis ->
                XMLSlideShow(fis).use { slideShow ->
                    if (slideIndex < slideShow.slides.size) {
                        val slide = slideShow.slides[slideIndex]
                        val textShapes = slide.shapes.filterIsInstance<XSLFTextShape>()
                        if (shapeIndex < textShapes.size) {
                            val textShape = textShapes[shapeIndex]
                            if (textShape.textParagraphs.isNotEmpty() && 
                                textShape.textParagraphs[0].textRuns.isNotEmpty()) {
                                textShape.textParagraphs[0].textRuns[0].setText(text)
                            } else {
                                // 创建新的文本运行
                                val para = textShape.addNewTextParagraph()
                                val run = para.addNewTextRun()
                                run.setText(text)
                            }
                        }
                    }
                    
                    FileOutputStream(file).use { fos ->
                        slideShow.write(fos)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "修改PPTX幻灯片文本失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 添加PPTX幻灯片
     */
    suspend fun addPptxSlide(file: File, layoutIndex: Int = 0): Result<Int> = withContext(Dispatchers.IO) {
        try {
            FileInputStream(file).use { fis ->
                XMLSlideShow(fis).use { slideShow ->
                    val slideMaster = slideShow.slideMasters[0]
                    val layouts = slideMaster.slideLayouts
                    val layout = if (layoutIndex < layouts.size) layouts[layoutIndex] else layouts[0]
                    // 创建新幻灯片
                    slideShow.createSlide(layout)
                    val slideIndex = slideShow.slides.size - 1
                    
                    FileOutputStream(file).use { fos ->
                        slideShow.write(fos)
                    }
                    
                    Result.success(slideIndex)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "添加PPTX幻灯片失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 删除PPTX幻灯片
     */
    suspend fun deletePptxSlide(file: File, slideIndex: Int): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            FileInputStream(file).use { fis ->
                XMLSlideShow(fis).use { slideShow ->
                    if (slideIndex < slideShow.slides.size) {
                        slideShow.removeSlide(slideIndex)
                    }
                    
                    FileOutputStream(file).use { fos ->
                        slideShow.write(fos)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "删除PPTX幻灯片失败", e)
            Result.failure(e)
        }
    }
    
    /**
     * 在PPTX幻灯片中添加文本形状
     */
    suspend fun addPptxTextShape(
        file: File,
        slideIndex: Int,
        text: String,
        x: Double = 100.0,
        y: Double = 100.0,
        width: Double = 200.0,
        height: Double = 50.0
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            FileInputStream(file).use { fis ->
                XMLSlideShow(fis).use { slideShow ->
                    if (slideIndex < slideShow.slides.size) {
                        val slide = slideShow.slides[slideIndex]
                        val textShape = slide.createTextBox()
                        // 使用 POI 的 Rectangle2D（POI-on-Android 提供了兼容实现）
                        try {
                            // 尝试使用 POI 的 Rectangle2D（如果可用）
                            val rectangleClass = Class.forName("org.apache.poi.sl.usermodel.Rectangle2D")
                            val rectangle = rectangleClass.getConstructor(Double::class.java, Double::class.java, Double::class.java, Double::class.java)
                                .newInstance(x, y, width, height)
                            val setAnchorMethod = textShape.javaClass.getMethod("setAnchor", rectangleClass)
                            setAnchorMethod.invoke(textShape, rectangle)
                        } catch (e: Exception) {
                            // 如果反射失败，使用默认位置（POI 会自动处理）
                            Log.w(TAG, "设置文本形状位置失败，使用默认位置", e)
                        }
                        val para = textShape.addNewTextParagraph()
                        val run = para.addNewTextRun()
                        run.setText(text)
                    }
                    
                    FileOutputStream(file).use { fos ->
                        slideShow.write(fos)
                    }
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "添加PPTX文本形状失败", e)
            Result.failure(e)
        }
    }
}


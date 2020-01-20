package cn.gjing.tools.excel.write;

import cn.gjing.tools.excel.MetaObject;
import cn.gjing.tools.excel.Type;
import cn.gjing.tools.excel.resolver.ExcelWriterResolver;
import org.apache.poi.hssf.usermodel.HSSFCell;
import org.apache.poi.hssf.usermodel.HSSFRow;
import org.apache.poi.hssf.usermodel.HSSFSheet;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

import javax.servlet.http.HttpServletResponse;
import java.io.Closeable;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.net.URLEncoder;
import java.util.List;

/**
 * XLS处理器
 *
 * @author Gjing
 **/
class ExcelWriteXLSResolver implements ExcelWriterResolver, Closeable {
    private HSSFWorkbook workbook;
    private OutputStream outputStream;
    private ExcelHelper excelHelper;
    private int times;

    @Override
    public void write(List<?> data, Workbook workbook, String sheetName, List<Field> headFieldList, MetaObject metaObject, boolean changed) {
        times++;
        this.workbook = (HSSFWorkbook) workbook;
        HSSFSheet sheet = this.workbook.getSheet(sheetName);
        if (sheet == null) {
            changed = true;
            sheet = this.workbook.createSheet(sheetName);
        }
        //Read the default offset
        int offset;
        if (times > 2) {
            offset = sheet.getLastRowNum()-1;
        }else {
            offset = sheet.getLastRowNum() == 0 ? 0 : sheet.getLastRowNum() + 1;
        }
        if (excelHelper == null) {
            this.excelHelper = new ExcelHelper(this.workbook, Type.XLS);
        }
        HSSFRow row;
        HSSFCell cell;
        if (metaObject.getBigTitle() != null) {
            int titleOffset = offset + metaObject.getBigTitle().getLastRow() - 1;
            sheet.addMergedRegion(new CellRangeAddress(offset, titleOffset, 0, headFieldList.size() - 1));
            for (int i = 0; i < metaObject.getBigTitle().getLastRow(); i++) {
                row = sheet.createRow(offset + i);
                for (int j = 0; j < headFieldList.size(); j++) {
                    cell = row.createCell(j);
                    cell.setCellValue(metaObject.getBigTitle().getContent());
                    cell.setCellStyle(metaObject.getMetaStyle().getTitleStyle());
                }
            }
            this.excelHelper.setVal(data, headFieldList, sheet, changed, titleOffset,metaObject);
        } else {
            this.excelHelper.setVal(data, headFieldList, sheet, changed, offset,metaObject);
        }
    }

    @Override
    public void flush(HttpServletResponse response, String fileName) {
        response.setContentType("application/vnd.ms-excel");
        try {
            response.setHeader("Content-disposition",
                    "attachment;filename=" + URLEncoder.encode(fileName, "UTF-8") + ".xls");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        try {
            this.outputStream = response.getOutputStream();
            this.workbook.write(this.outputStream);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws IOException {
        if (this.outputStream != null) {
            this.outputStream.flush();
            this.outputStream.close();
        }
        if (this.workbook != null) {
            this.workbook.close();
        }
    }
}

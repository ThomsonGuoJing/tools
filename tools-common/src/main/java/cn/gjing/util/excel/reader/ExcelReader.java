package cn.gjing.util.excel.reader;

import cn.gjing.util.BeanUtil;
import cn.gjing.util.ParamUtil;
import cn.gjing.util.TimeUtil;
import cn.gjing.util.excel.Excel;
import cn.gjing.util.excel.ExcelField;
import cn.gjing.util.id.IdUtil;
import com.monitorjbl.xlsx.StreamingReader;
import org.apache.poi.hssf.usermodel.HSSFDateUtil;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Excel导入
 *
 * @author Gjing
 **/
@SuppressWarnings("unused")
public class ExcelReader<T> implements AutoCloseable {
    /**
     * id生成器
     */
    private IdUtil idUtil;

    /**
     * excel关联的实体class
     */
    private Class<T> excelClass;

    /**
     * excel注解
     */
    private Excel excel;

    /**
     * 工作簿
     */
    private Workbook workbook;

    /**
     * 输入流
     */
    private InputStream inputStream;

    /**
     * sheet
     */
    private Sheet sheet;

    private Map<String, Field> hasAnnotationFieldMap = new HashMap<>(16);

    /**
     * 读取到的数据
     */
    private List<T> data = new ArrayList<>();

    private ExcelReader() {

    }

    private ExcelReader(Class<T> excelClass, InputStream inputStream, IdUtil idUtil) {
        this.excelClass = excelClass;
        this.inputStream = inputStream;
        this.idUtil = idUtil;
    }

    public static <T> ExcelReader<T> of(Class<T> excelClass, InputStream inputStream) {
        return new ExcelReader<>(excelClass, inputStream, null);
    }

    public static <T> ExcelReader<T> of(Class<T> excelClass, InputStream inputStream, IdUtil idUtil) {
        return new ExcelReader<>(excelClass, inputStream, idUtil);
    }

    /**
     * 进行读excel
     */
    public List<T> doRead() {
        //得到excel的class实体
        Excel excel = this.excelClass.getAnnotation(Excel.class);
        if (excel == null) {
            throw new NullPointerException("@Excel was not found on the excelClass");
        }
        this.excel = excel;
        //拿到所有带有注解的字段，放进map中
        this.hasAnnotationFieldMap = Arrays.stream(this.excelClass.getDeclaredFields())
                .filter(f -> f.isAnnotationPresent(ExcelField.class))
                .collect(
                        Collectors.toMap(field -> field.getAnnotation(ExcelField.class).name(), field -> field)
                );
        //找找是否有父类，有就加进来
        Class<? super T> superclass = excelClass.getSuperclass();
        if (superclass != Object.class) {
            Map<String, Field> supperFieldMap = Arrays.stream(superclass.getDeclaredFields())
                    .filter(f -> f.isAnnotationPresent(ExcelField.class))
                    .collect(
                            Collectors.toMap(field -> field.getAnnotation(ExcelField.class).name(), field -> field)
                    );
            this.hasAnnotationFieldMap.putAll(supperFieldMap);
        }
        switch (excel.type()) {
            case XLS:
                try {
                    this.workbook = new HSSFWorkbook(inputStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                //得到sheet
                this.sheet = this.workbook.getSheetAt(0);
                reader();
                return this.data;
            case XLSX:
                this.workbook = StreamingReader.builder().rowCacheSize(100).bufferSize(4096).open(inputStream);
                this.sheet = this.workbook.getSheetAt(0);
                reader();
                return this.data;
            default:
                throw new NullPointerException("Doc type was not found");
        }
    }

    /**
     * excel读者
     */
    private void reader() {
        //excel列表头名称
        List<String> headNameList = new ArrayList<>();
        //excel内容的开始下标
        int rowIndex = ParamUtil.equals("", this.excel.description()) ? 1 : this.excel.lastRow() + 2;
        //excel列表头下标
        int headerIndex = rowIndex - 1;
        //获取excel列表头这行, 并设置总列数和列表头名称
        int totalCell = 0;
        //创建个新实例
        T o = null;
        for (Row row : sheet) {
            if (row.getRowNum() < rowIndex) {
                if (row.getRowNum() == headerIndex) {
                    //增加列表头名称和总列数
                    for (Cell cell : row) {
                        totalCell++;
                        headNameList.add(cell.getStringCellValue());
                    }
                }
                continue;
            }
            try {
                o = excelClass.newInstance();
            } catch (InstantiationException | IllegalAccessException e) {
                e.printStackTrace();
            }
            for (int c = 0; c < totalCell; c++) {
                Field field = hasAnnotationFieldMap.get(headNameList.get(c));
                if (field == null) {
                    continue;
                }
                ExcelField excelField = field.getAnnotation(ExcelField.class);
                //如果设置了id生成策略，就走自动生成
                if (excelField.strategy() != Generate.NONE) {
                    switch (excelField.strategy()) {
                        case UUID:
                            this.setField(field, o, this.idUtil.uuid());
                            break;
                        case SNOW_ID:
                            this.setField(field, o, this.idUtil.snowId());
                            break;
                        default:
                    }
                    continue;
                }
                Cell cell = row.getCell(c);
                if (cell != null) {
                    this.setValue(o, field, this.getValue(cell), excelField);
                }
            }
            this.data.add(o);
        }
    }

    /**
     * 获取值
     *
     * @param cell 单元格
     * @return string
     */
    private String getValue(Cell cell) {
        Object value = "";
        switch (cell.getCellType()) {
            case _NONE:
            case BLANK:
            case ERROR:
                break;
            case BOOLEAN:
                value = cell.getBooleanCellValue();
                break;
            case NUMERIC:
                if (HSSFDateUtil.isCellDateFormatted(cell)) {
                    value = TimeUtil.dateToString(cell.getDateCellValue());
                } else {
                    value = cell.getNumericCellValue();
                }
                break;
            case FORMULA:
                value = cell.getCellFormula();
                break;
            default:
                value = cell.getStringCellValue();
                break;
        }
        return value.toString();
    }

    /**
     * 给对象的字段设置值
     *
     * @param o          具体对象
     * @param field      字段
     * @param value      excel读出来的值
     * @param excelField excel字段注解
     */
    @SuppressWarnings("unchecked")
    private void setValue(Object o, Field field, String value, ExcelField excelField) {
        if (field.getType() == String.class) {
            this.setField(field, o, value);
            return;
        }
        if (field.getType() == int.class || field.getType() == Integer.class) {
            int i = Double.valueOf(value).intValue();
            System.out.println(i);
            this.setField(field, o, Double.valueOf(value).intValue());
            return;
        }
        if (field.getType() == long.class || field.getType() == Long.class) {
            this.setField(field, o, Double.valueOf(value).longValue());
            return;
        }
        if (field.getType() == boolean.class || field.getType() == Boolean.class) {
            this.setField(field, o, Boolean.parseBoolean(value));
            return;
        }
        if (field.getType() == Date.class) {
            this.setField(field, o, ParamUtil.equals("",excelField.pattern())
                    ? TimeUtil.stringToDate(value)
                    : TimeUtil.stringToDate(value, excelField.pattern()));
            return;
        }
        if (field.getType().isEnum()) {
            Class<? extends Enum> type = (Class<? extends Enum>) field.getType();
            Object invoke = null;
            try {
                Method to = BeanUtil.findDeclaredMethod(field.getType(), "to", Object.class);
                assert to != null;
                invoke = to.invoke(Enum.valueOf(type, type.getEnumConstants()[0].toString()), value);
            } catch (Exception e) {
                e.printStackTrace();
            }
            this.setField(field, o, invoke);
            return;
        }
        if (field.getType() == double.class || field.getType() == Double.class) {
            this.setField(field, o, Double.parseDouble(value));
            return;
        }
        if (field.getType() == float.class || field.getType() == Float.class) {
            this.setField(field, o, Float.parseFloat(value));
            return;
        }
        if (field.getType() == byte.class || field.getType() == Byte.class) {
            this.setField(field, o, Byte.parseByte(value));
        }

    }

    /**
     * 字段set值
     *
     * @param field 字段
     * @param o     字段所在对象
     * @param value 值
     */
    private void setField(Field field, Object o, Object value) {
        BeanUtil.setFieldValue(o, field, value);
    }

    /**
     * 关闭流
     *
     * @throws IOException io
     */
    @Override
    public void close() throws IOException {
        if (inputStream != null) {
            this.inputStream.close();
        }
    }

}

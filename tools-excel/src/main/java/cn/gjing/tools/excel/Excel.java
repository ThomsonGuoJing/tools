package cn.gjing.tools.excel;


import cn.gjing.tools.excel.metadata.ExcelType;

import java.lang.annotation.*;

import static cn.gjing.tools.excel.metadata.ExcelType.XLS;

/**
 * Excel mapped entity annotation
 *
 * @author Gjing
 **/
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Excel {
    /**
     * Excel file name
     *
     * @return name
     */
    String value() default "";

    /**
     * Excel file type
     *
     * @return Type
     */
    ExcelType type() default XLS;

    /**
     * Window size, which is flushed to disk when exported
     * if the data that has been written out exceeds the specified size
     * only for xlsx
     *
     * @return 500
     */
    int windowSize() default 500;

    /**
     * How many lines of data in the Excel file need to be saved when imported,
     * only for xlsx
     *
     * @return 500
     */
    int cacheRowSize() default 100;

    /**
     * buffer size to use when reading InputStream to file
     *
     * @return 10240
     */
    int bufferSize() default 2048;
}

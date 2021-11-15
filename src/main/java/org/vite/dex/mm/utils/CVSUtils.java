package org.vite.dex.mm.utils;

import com.opencsv.CSVWriter;
import com.opencsv.bean.StatefulBeanToCsv;
import com.opencsv.bean.StatefulBeanToCsvBuilder;
import com.opencsv.exceptions.CsvDataTypeMismatchException;
import com.opencsv.exceptions.CsvRequiredFieldEmptyException;
import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

public class CVSUtils {

    // export to CSV File
    public static <T> void exportToCSV(List<T> list, String fileName)
            throws IOException, CsvDataTypeMismatchException, CsvRequiredFieldEmptyException {
        try (Writer writer = Files.newBufferedWriter(Paths.get(fileName));
                CSVWriter csvWriter = new CSVWriter(writer,
                        CSVWriter.DEFAULT_SEPARATOR,
                        CSVWriter.NO_QUOTE_CHARACTER,
                        CSVWriter.DEFAULT_ESCAPE_CHARACTER,
                        CSVWriter.DEFAULT_LINE_END);) {

            String[] headerRecord = {"cycleKey", "address", "order_mining_amount", "order_mining_percent",
                    "invite_mining_amount", "invite_mining_percent", "total_reward"};
            csvWriter.writeNext(headerRecord);

            StatefulBeanToCsv<T> beanToCsv = new StatefulBeanToCsvBuilder(writer)
                    .withQuotechar(CSVWriter.NO_QUOTE_CHARACTER)
                    .build();
            beanToCsv.write(list);
        }
    }
}

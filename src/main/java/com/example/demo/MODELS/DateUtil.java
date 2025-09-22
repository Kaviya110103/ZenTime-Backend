package com.example.demo.MODELS;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class DateUtil {

    private static final DateTimeFormatter DB_FMT   = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter ISO_FMT  = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** Convert dd/MM/yyyy -> yyyy-MM-dd */
    public static String dbToIso(String dbDate) {
        LocalDate d = LocalDate.parse(dbDate, DB_FMT);
        return d.format(ISO_FMT);
    }

    /** Convert yyyy-MM-dd -> dd/MM/yyyy */
    public static String isoToDb(String isoDate) {
        LocalDate d = LocalDate.parse(isoDate, ISO_FMT);
        return d.format(DB_FMT);
    }

    /** First and last date of month in db (dd/MM/yyyy) format */
    public static String dbStartOfMonth(int year, int month) {
        return LocalDate.of(year, month, 1).format(DB_FMT);
    }

    public static String dbEndOfMonth(int year, int month) {
        return LocalDate.of(year, month, 1)
                .withDayOfMonth(LocalDate.of(year, month, 1).lengthOfMonth())
                .format(DB_FMT);
    }
}
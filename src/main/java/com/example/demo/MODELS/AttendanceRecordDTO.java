package com.example.demo.MODELS;

import java.util.Base64;

public record AttendanceRecordDTO(
        String date,
        String attendanceStatus,
        String dayStatus,
        String timeIn,
        String timeOut,
        String location,
        String timoutReason,
        Integer missedTimes,
        String imageInBase64,
        String imageOutBase64
) {
    public static AttendanceRecordDTO fromEntity(AttendanceRecord ar) {
        return new AttendanceRecordDTO(
                ar.getDate(),
                ar.getAttendanceStatus(),
                ar.getDayStatus(),
                ar.getTimeIn()  != null ? ar.getTimeIn().toString()  : null,
                ar.getTimeOut() != null ? ar.getTimeOut().toString() : null,
                ar.getLocation(),
                ar.getTimoutReason(),
                ar.getMissedTimes(),
                ar.getImageIn()  != null ? Base64.getEncoder().encodeToString(ar.getImageIn())  : null,
                ar.getImageOut() != null ? Base64.getEncoder().encodeToString(ar.getImageOut()) : null
        );
    }
}

package com.example.demo.service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.Month;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

@Service
public class PayslipPdfService {

    public record AllowanceItem(String name, double amount) {}

    public record PayslipData(
            String companyName,
            Long employeeId,
            String employeeName,
            String position,
            String branch,
            String mobile,
            String email,
            int month,
            int year,
            int totalDays,
            int scheduledDays,
            int weekOffDays,
            String holidaysSummary,
            int workedDays,
            int absentDays,
            double expectedHours,
            double payableHours,
            double overtimeHours,
            double missingHours,
            double basicSalary,
            double netSalary,
            double convenience,
            double otAmount,
            double pfAmount,
            double lopAmount,
            double incentives,
            double advance,
            double others,
            double allowancesTotal,
            List<AllowanceItem> allowances) {}

    private record PayslipLine(String label, double amount) {}

    public byte[] generatePdf(PayslipData data, PayrollLogoStorageService.LogoData logoData) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 20f, 20f, 16f, 16f);
        PdfWriter.getInstance(document, outputStream);
        document.open();

        Font companyFont = new Font(Font.HELVETICA, 20, Font.BOLD, new Color(51, 51, 51));
        Font locationFont = new Font(Font.HELVETICA, 12, Font.NORMAL, new Color(51, 51, 51));
        Font titleFont = new Font(Font.HELVETICA, 15, Font.BOLD, new Color(34, 34, 34));
        Font sectionHeaderFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(34, 34, 34));
        Font valueFont = new Font(Font.HELVETICA, 11, Font.NORMAL, new Color(34, 34, 34));
        Font smallFont = new Font(Font.HELVETICA, 10, Font.NORMAL, new Color(34, 34, 34));
        Font netPayLabelFont = new Font(Font.HELVETICA, 14, Font.BOLD, new Color(34, 34, 34));
        Font netPayAmountFont = new Font(Font.HELVETICA, 44, Font.BOLD, new Color(0, 0, 0));
        Font boldCellFont = new Font(Font.HELVETICA, 11, Font.BOLD, new Color(34, 34, 34));

        String monthName = Month.of(Math.min(Math.max(data.month(), 1), 12))
                .getDisplayName(java.time.format.TextStyle.FULL, Locale.ENGLISH);
        String payPeriod = monthName + " " + data.year();
        int lopDays = estimateLopDays(data);

        List<PayslipLine> earnings = buildEarnings(data);
        List<PayslipLine> deductions = buildDeductions(data);
        double grossEarnings = earnings.stream().mapToDouble(PayslipLine::amount).sum();
        double totalDeductions = deductions.stream().mapToDouble(PayslipLine::amount).sum();
        double totalNetPayable = data.netSalary();

        PdfPTable header = new PdfPTable(new float[] { 4.6f, 1.4f });
        header.setWidthPercentage(100);
        header.addCell(companyBlock(
                nonBlank(data.companyName(), "ZenTime"),
                nonBlank(data.branch(), "-"),
                companyFont,
                locationFont));
        header.addCell(logoBlock(logoData));
        document.add(header);

        PdfPTable titleTable = new PdfPTable(1);
        titleTable.setWidthPercentage(100);
        PdfPCell titleCell = cell("Payslip for the month of " + payPeriod, titleFont, Element.ALIGN_CENTER, true);
        titleCell.setPaddingTop(10f);
        titleCell.setPaddingBottom(10f);
        titleTable.addCell(titleCell);
        document.add(titleTable);

        PdfPTable summary = new PdfPTable(new float[] { 2.9f, 2.4f });
        summary.setWidthPercentage(100);
        summary.addCell(paySummaryBlock(data, payPeriod, sectionHeaderFont, valueFont));
        summary.addCell(netPayBlock(totalNetPayable, data.workedDays(), lopDays, netPayLabelFont, netPayAmountFont, valueFont));
        document.add(summary);

        PdfPTable earningsDeductionsTable = new PdfPTable(new float[] { 2.8f, 1.5f, 1.4f, 2.1f, 1.3f, 1.4f });
        earningsDeductionsTable.setWidthPercentage(100);
        addHead(earningsDeductionsTable, "EARNINGS", sectionHeaderFont);
        addHead(earningsDeductionsTable, "AMOUNT", sectionHeaderFont);
        addHead(earningsDeductionsTable, "YTD", sectionHeaderFont);
        addHead(earningsDeductionsTable, "DEDUCTIONS", sectionHeaderFont);
        addHead(earningsDeductionsTable, "AMOUNT", sectionHeaderFont);
        addHead(earningsDeductionsTable, "YTD", sectionHeaderFont);

        int rows = Math.max(earnings.size(), deductions.size());
        for (int i = 0; i < rows; i++) {
            PayslipLine earning = i < earnings.size() ? earnings.get(i) : null;
            PayslipLine deduction = i < deductions.size() ? deductions.get(i) : null;
            addLine(earningsDeductionsTable, earning, valueFont);
            addMoney(earningsDeductionsTable, earning == null ? null : earning.amount(), valueFont);
            addMoney(earningsDeductionsTable, earning == null ? null : earning.amount(), valueFont);
            addLine(earningsDeductionsTable, deduction, valueFont);
            addMoney(earningsDeductionsTable, deduction == null ? null : deduction.amount(), valueFont);
            addMoney(earningsDeductionsTable, deduction == null ? null : deduction.amount(), valueFont);
        }

        PdfPCell grossLabel = cell("Gross Earnings", boldCellFont, Element.ALIGN_LEFT, true);
        grossLabel.setColspan(1);
        earningsDeductionsTable.addCell(grossLabel);
        addMoney(earningsDeductionsTable, grossEarnings, boldCellFont);
        earningsDeductionsTable.addCell(cell("", valueFont, Element.ALIGN_CENTER, true));
        PdfPCell dedLabel = cell("Total Deductions", boldCellFont, Element.ALIGN_LEFT, true);
        earningsDeductionsTable.addCell(dedLabel);
        addMoney(earningsDeductionsTable, totalDeductions, boldCellFont);
        earningsDeductionsTable.addCell(cell("", valueFont, Element.ALIGN_CENTER, true));
        document.add(earningsDeductionsTable);

        PdfPTable netPayTable = new PdfPTable(new float[] { 4.5f, 1.5f });
        netPayTable.setWidthPercentage(100);
        PdfPCell netHeaderLeft = shadedCell("NET PAY", sectionHeaderFont, Element.ALIGN_LEFT);
        netPayTable.addCell(netHeaderLeft);
        PdfPCell netHeaderRight = shadedCell("AMOUNT", sectionHeaderFont, Element.ALIGN_RIGHT);
        netPayTable.addCell(netHeaderRight);
        netPayTable.addCell(cell("Gross Earnings", valueFont, Element.ALIGN_LEFT, true));
        netPayTable.addCell(cell(money(grossEarnings), valueFont, Element.ALIGN_RIGHT, true));
        netPayTable.addCell(cell("Total Deductions", valueFont, Element.ALIGN_LEFT, true));
        netPayTable.addCell(cell("(-) " + money(totalDeductions), valueFont, Element.ALIGN_RIGHT, true));
        netPayTable.addCell(cell("Total Net Payable", boldCellFont, Element.ALIGN_RIGHT, true));
        netPayTable.addCell(cell(money(totalNetPayable), boldCellFont, Element.ALIGN_RIGHT, true));
        document.add(netPayTable);

        Paragraph payableLine = new Paragraph(
                "Total Net Payable " + money(totalNetPayable)
                        + " (Indian Rupee " + toIndianWords(Math.round(totalNetPayable)) + " Only)",
                new Font(Font.HELVETICA, 15, Font.BOLD, new Color(34, 34, 34)));
        payableLine.setAlignment(Element.ALIGN_CENTER);
        payableLine.setSpacingBefore(16f);
        document.add(payableLine);

        Paragraph formula = new Paragraph("**Total Net Payable = Gross Earnings - Total Deductions", smallFont);
        formula.setAlignment(Element.ALIGN_CENTER);
        formula.setSpacingBefore(4f);
        document.add(formula);

        Paragraph footer = new Paragraph(
                "-- This document has been automatically generated by ZenTime Payroll; therefore, a signature is not required. --",
                new Font(Font.HELVETICA, 9, Font.NORMAL, new Color(80, 80, 80)));
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(18f);
        document.add(footer);

        document.close();
        return outputStream.toByteArray();
    }

    private PdfPCell companyBlock(String companyName, String location, Font companyFont, Font locationFont) {
        Paragraph p = new Paragraph();
        p.add(new Phrase(companyName + "\n", companyFont));
        p.add(new Phrase(location, locationFont));
        PdfPCell cell = new PdfPCell(p);
        cell.setBorder(Rectangle.BOX);
        cell.setPadding(10f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        return cell;
    }

    private PdfPCell logoBlock(PayrollLogoStorageService.LogoData logoData) throws Exception {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setPadding(10f);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        if (logoData != null && logoData.bytes() != null && logoData.bytes().length > 0) {
            Image image = Image.getInstance(logoData.bytes());
            image.scaleToFit(84f, 48f);
            image.setAlignment(Element.ALIGN_RIGHT);
            cell.addElement(image);
        } else {
            cell.addElement(new Paragraph(""));
        }
        return cell;
    }

    private PdfPCell paySummaryBlock(
            PayslipData data,
            String payPeriod,
            Font headerFont,
            Font valueFont) {
        PdfPTable wrapper = new PdfPTable(1);
        wrapper.setWidthPercentage(100);

        PdfPCell titleCell = new PdfPCell(new Phrase("EMPLOYEE PAY SUMMARY", headerFont));
        titleCell.setBorder(Rectangle.NO_BORDER);
        titleCell.setPaddingTop(2f);
        titleCell.setPaddingBottom(8f);
        wrapper.addCell(titleCell);

        PdfPTable details = new PdfPTable(new float[] { 1.7f, 0.15f, 2.35f });
        details.setWidthPercentage(100);

        addSummaryDetailRow(details, "Employee Name", nonBlank(data.employeeName(), "-") + ", " + safe(data.employeeId()), valueFont);
        addSummaryDetailRow(details, "Designation", nonBlank(data.position(), "-"), valueFont);
        addSummaryDetailRow(details, "Date of Joining", "-", valueFont);
        addSummaryDetailRow(details, "Pay Period", payPeriod, valueFont);
        addSummaryDetailRow(details, "Pay Date", LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")), valueFont);

        PdfPCell detailsCell = new PdfPCell(details);
        detailsCell.setBorder(Rectangle.NO_BORDER);
        detailsCell.setPadding(0f);
        wrapper.addCell(detailsCell);

        PdfPCell wrapperCell = new PdfPCell(wrapper);
        wrapperCell.setBorder(Rectangle.BOX);
        wrapperCell.setPadding(10f);
        wrapperCell.setVerticalAlignment(Element.ALIGN_TOP);
        return wrapperCell;
    }

    private void addSummaryDetailRow(PdfPTable details, String label, String value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(nonBlank(label, "-"), font));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPaddingTop(3f);
        labelCell.setPaddingBottom(3f);
        labelCell.setPaddingLeft(0f);
        details.addCell(labelCell);

        PdfPCell colonCell = new PdfPCell(new Phrase(":", font));
        colonCell.setBorder(Rectangle.NO_BORDER);
        colonCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        colonCell.setPaddingTop(3f);
        colonCell.setPaddingBottom(3f);
        details.addCell(colonCell);

        PdfPCell valueCell = new PdfPCell(new Phrase(nonBlank(value, "-"), font));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPaddingTop(3f);
        valueCell.setPaddingBottom(3f);
        valueCell.setPaddingRight(0f);
        details.addCell(valueCell);
    }

    private PdfPCell netPayBlock(
            double totalNetPayable,
            int paidDays,
            int lopDays,
            Font labelFont,
            Font amountFont,
            Font valueFont) {
        PdfPCell cell = new PdfPCell();
        cell.setBorder(Rectangle.BOX);
        cell.setPaddingTop(16f);
        cell.setPaddingBottom(14f);
        cell.setPaddingLeft(10f);
        cell.setPaddingRight(10f);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);

        Paragraph label = new Paragraph("Employee Net Pay", labelFont);
        label.setAlignment(Element.ALIGN_CENTER);
        label.setSpacingBefore(2f);
        label.setSpacingAfter(12f);

        Paragraph amount = new Paragraph(money(totalNetPayable), amountFont);
        amount.setAlignment(Element.ALIGN_CENTER);
        amount.setSpacingBefore(0f);
        amount.setSpacingAfter(14f);

        Paragraph days = new Paragraph("Paid Days : " + paidDays + " | LOP Days : " + lopDays, valueFont);
        days.setAlignment(Element.ALIGN_CENTER);
        days.setSpacingBefore(0f);
        days.setSpacingAfter(2f);

        cell.addElement(label);
        cell.addElement(amount);
        cell.addElement(days);
        return cell;
    }

    private List<PayslipLine> buildEarnings(PayslipData data) {
        List<PayslipLine> lines = new ArrayList<>();
        lines.add(new PayslipLine("Basic", safeAmount(data.basicSalary())));
        if (data.incentives() > 0) {
            lines.add(new PayslipLine("Incentives", safeAmount(data.incentives())));
        }
        if (data.convenience() > 0) {
            lines.add(new PayslipLine("Convenience", safeAmount(data.convenience())));
        }
        if (data.otAmount() > 0) {
            lines.add(new PayslipLine("OT", safeAmount(data.otAmount())));
        }
        if (data.allowances() != null) {
            for (AllowanceItem allowance : data.allowances()) {
                if (allowance != null && allowance.amount() > 0) {
                    lines.add(new PayslipLine(nonBlank(allowance.name(), "Allowance"), safeAmount(allowance.amount())));
                }
            }
        }
        if (lines.size() == 1 && data.allowancesTotal() > 0) {
            lines.add(new PayslipLine("Additional Allowance", safeAmount(data.allowancesTotal())));
        }
        return lines;
    }

    private List<PayslipLine> buildDeductions(PayslipData data) {
        List<PayslipLine> lines = new ArrayList<>();
        if (data.pfAmount() > 0) {
            lines.add(new PayslipLine("PF", safeAmount(data.pfAmount())));
        }
        if (data.lopAmount() > 0) {
            lines.add(new PayslipLine("LOP", safeAmount(data.lopAmount())));
        }
        if (data.advance() > 0) {
            lines.add(new PayslipLine("Advance", safeAmount(data.advance())));
        }
        if (data.others() > 0) {
            lines.add(new PayslipLine("Others", safeAmount(data.others())));
        }
        return lines;
    }

    private int estimateLopDays(PayslipData data) {
        if (data.totalDays() <= 0 || data.basicSalary() <= 0 || data.lopAmount() <= 0) {
            return 0;
        }
        double perDay = data.basicSalary() / data.totalDays();
        if (perDay <= 0) {
            return 0;
        }
        return (int) Math.round(data.lopAmount() / perDay);
    }

    private void addHead(PdfPTable table, String text, Font font) {
        PdfPCell cell = shadedCell(text, font, Element.ALIGN_LEFT);
        if ("AMOUNT".equals(text) || "YTD".equals(text)) {
            cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        }
        table.addCell(cell);
    }

    private void addLine(PdfPTable table, PayslipLine line, Font font) {
        table.addCell(cell(line == null ? "" : line.label(), font, Element.ALIGN_LEFT, true));
    }

    private void addMoney(PdfPTable table, Double amount, Font font) {
        table.addCell(cell(amount == null ? "" : money(amount), font, Element.ALIGN_RIGHT, true));
    }

    private PdfPCell shadedCell(String value, Font font, int align) {
        PdfPCell cell = cell(value, font, align, true);
        cell.setBackgroundColor(new Color(240, 240, 240));
        return cell;
    }

    private PdfPCell cell(String value, Font font, int align, boolean border) {
        PdfPCell cell = new PdfPCell(new Phrase(nonBlank(value, ""), font));
        cell.setHorizontalAlignment(align);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setPadding(8f);
        cell.setBorder(border ? Rectangle.BOX : Rectangle.NO_BORDER);
        return cell;
    }

    private String money(double value) {
        return "\u20B9" + fmt(value);
    }

    private String fmt(double value) {
        return new DecimalFormat("#,##0.00").format(safeAmount(value));
    }

    private double safeAmount(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String safe(Long value) {
        return value == null ? "-" : String.valueOf(value);
    }

    private String nonBlank(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value;
    }

    private String toIndianWords(long value) {
        if (value == 0) {
            return "Zero";
        }
        if (value < 0) {
            return "Minus " + toIndianWords(-value);
        }

        StringBuilder result = new StringBuilder();
        long crore = value / 10000000;
        value %= 10000000;
        long lakh = value / 100000;
        value %= 100000;
        long thousand = value / 1000;
        value %= 1000;
        long hundredPart = value;

        if (crore > 0) {
            result.append(convertBelowThousand((int) crore)).append(" Crore ");
        }
        if (lakh > 0) {
            result.append(convertBelowThousand((int) lakh)).append(" Lakh ");
        }
        if (thousand > 0) {
            result.append(convertBelowThousand((int) thousand)).append(" Thousand ");
        }
        if (hundredPart > 0) {
            result.append(convertBelowThousand((int) hundredPart)).append(" ");
        }

        return result.toString().trim().replaceAll("\\s+", " ");
    }

    private String convertBelowThousand(int number) {
        final String[] units = {
                "", "One", "Two", "Three", "Four", "Five", "Six", "Seven", "Eight", "Nine",
                "Ten", "Eleven", "Twelve", "Thirteen", "Fourteen", "Fifteen", "Sixteen",
                "Seventeen", "Eighteen", "Nineteen"
        };
        final String[] tens = {
                "", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"
        };

        StringBuilder words = new StringBuilder();
        if (number >= 100) {
            words.append(units[number / 100]).append(" Hundred");
            number %= 100;
            if (number > 0) {
                words.append(" ");
            }
        }
        if (number >= 20) {
            words.append(tens[number / 10]);
            number %= 10;
            if (number > 0) {
                words.append(" ");
            }
        }
        if (number > 0 && number < 20) {
            words.append(units[number]);
        }
        return words.toString().trim();
    }
}

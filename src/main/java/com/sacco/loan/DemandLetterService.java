package com.sacco.loan;

import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sacco.loan.dto.LoanGuarantorResponse;
import com.sacco.loan.dto.LoanScheduleResponse;
import com.sacco.loan.entity.Loan;
import com.sacco.loan.entity.LoanDemand;
import com.sacco.loan.entity.LoanProduct;
import com.sacco.member.entity.Member;
import com.sacco.setup.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Renders a formal demand notice as a print-ready A4 PDF. The letter carries a
 * document seal (SHA-256) generated when the demand was registered so a printed
 * copy can be reconciled with the collections register.
 */
@Service
@RequiredArgsConstructor
public class DemandLetterService {

    private final SystemSettingsService systemSettingsService;

    private static final Color NAVY = new Color(26, 35, 50);
    private static final Color ORANGE = new Color(230, 81, 0);
    private static final Color GREY = new Color(100, 116, 139);
    private static final Color LIGHT_GREY = new Color(241, 245, 249);
    private static final Color RED = new Color(198, 40, 40);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DATETIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");
    private static final DecimalFormat MONEY = new DecimalFormat("#,##0");

    public byte[] generate(LoanDemand demand,
            Loan loan,
            Member member,
            LoanProduct product,
            List<LoanScheduleResponse> schedule,
            List<LoanGuarantorResponse> guarantors,
            String issuedByName) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 42, 42, 46, 46);
        try {
            PdfWriter.getInstance(document, output);
            document.open();

            Font title = FontFactory.getFont(FontFactory.HELVETICA, 16, Font.BOLD, NAVY);
            Font subtitle = FontFactory.getFont(FontFactory.HELVETICA, 9, Font.NORMAL, GREY);
            Font heading = FontFactory.getFont(FontFactory.HELVETICA, 11, Font.BOLD, NAVY);
            Font body = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, Font.NORMAL, NAVY);
            Font bodyBold = FontFactory.getFont(FontFactory.HELVETICA, 9.5f, Font.BOLD, NAVY);
            Font small = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, GREY);
            Font tableHeader = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.BOLD, Color.WHITE);
            Font tableBody = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.NORMAL, NAVY);
            Font tableBodyBold = FontFactory.getFont(FontFactory.HELVETICA, 8, Font.BOLD, NAVY);
            Font alert = FontFactory.getFont(FontFactory.HELVETICA, 10, Font.BOLD, RED);

            Paragraph organisation = new Paragraph(
                    systemSettingsService.organizationName().toUpperCase(Locale.ROOT), title);
            organisation.setSpacingAfter(2f);
            document.add(organisation);
            String contactLine = systemSettingsService.organizationContactLine();
            Paragraph department = new Paragraph(
                    "Member Credit & Collections Department", subtitle);
            department.setSpacingAfter(contactLine.isBlank() ? 10f : 2f);
            document.add(department);
            if (!contactLine.isBlank()) {
                Paragraph contact = new Paragraph(contactLine, subtitle);
                contact.setSpacingAfter(10f);
                document.add(contact);
            }

            PdfPTable rule = new PdfPTable(1);
            rule.setWidthPercentage(100);
            PdfPCell ruleCell = new PdfPCell(new Phrase(" "));
            ruleCell.setBorder(Rectangle.NO_BORDER);
            ruleCell.setBackgroundColor(ORANGE);
            ruleCell.setFixedHeight(3f);
            rule.addCell(ruleCell);
            rule.setSpacingAfter(14f);
            document.add(rule);

            Paragraph noticeTitle = new Paragraph(stageTitle(demand.getStage()), title);
            noticeTitle.setSpacingAfter(4f);
            document.add(noticeTitle);
            Paragraph reference = new Paragraph(
                    "Ref: " + demand.getDemandNumber()
                            + "     |     Issued: " + demand.getIssuedAt().format(DATETIME_FORMAT)
                            + "     |     Payment deadline: " + demand.getDueDate().format(DATE_FORMAT),
                    small);
            reference.setSpacingAfter(12f);
            document.add(reference);

            document.add(sectionHeading("MEMBER", heading));
            document.add(twoColumnBlock(
                    new String[]{
                            "Member", value(member.getFirstName() + " " + member.getLastName()),
                            "Membership No.", value(member.getMembershipNo()),
                            "Phone", value(member.getPhone()),
                            "Email", value(member.getEmail()),
                            "Address", value(member.getAddress())
                    }, body, bodyBold));

            document.add(sectionHeading("LOAN ACCOUNT", heading));
            document.add(twoColumnBlock(
                    new String[]{
                            "Loan number", value(loan.getLoanNumber()),
                            "Product", value(product == null ? null : product.getName()),
                            "Disbursed", value(loan.getDisbursedAt() == null ? null
                                    : loan.getDisbursedAt().format(DATE_FORMAT)),
                            "Term", value(loan.getTermMonths() + " months"),
                            "Interest rate", value(loan.getInterestRate().stripTrailingZeros()
                                    .toPlainString() + "%"),
                            "Days overdue", value(String.valueOf(demand.getDaysOverdue()))
                    }, body, bodyBold));

            document.add(sectionHeading("ARREARS AS AT "
                    + demand.getIssuedAt().toLocalDate().format(DATE_FORMAT), heading));
            Paragraph statement = new Paragraph(
                    "The above loan account is in arrears. The overdue installments and the amount "
                            + "now demanded are set out below. This notice is issued in accordance with "
                            + "the loan agreement and the SACCO's credit policy.",
                    body);
            statement.setSpacingAfter(8f);
            document.add(statement);

            document.add(arrearsTable(schedule, demand, tableHeader, tableBody, tableBodyBold));

            Paragraph demandLine = new Paragraph();
            demandLine.setSpacingBefore(12f);
            demandLine.setSpacingAfter(8f);
            demandLine.add(new Phrase("DEMAND: ", alert));
            demandLine.add(new Phrase("Payment of ", body));
            demandLine.add(new Phrase("UGX " + money(demand.getAmountDemanded()), bodyBold));
            demandLine.add(new Phrase(" is hereby demanded on or before ", body));
            demandLine.add(new Phrase(demand.getDueDate().format(DATE_FORMAT), bodyBold));
            demandLine.add(new Phrase(". The amount comprises principal arrears of UGX "
                    + money(demand.getPrincipalArrears())
                    + ", interest arrears of UGX " + money(demand.getInterestArrears())
                    + " and accrued penalties of UGX " + money(demand.getPenaltyArrears())
                    + ".", body));
            document.add(demandLine);

            document.add(sectionHeading("PAYMENT INSTRUCTIONS", heading));
            Paragraph instructions = new Paragraph(
                    "Pay at any SACCO branch or through an authorised collection channel. Quote the "
                            + "loan number and demand reference on every payment. A receipt will be issued "
                            + "for each payment received.",
                    body);
            instructions.setSpacingAfter(8f);
            document.add(instructions);

            document.add(sectionHeading("IF PAYMENT IS NOT MADE", heading));
            com.lowagie.text.List consequences = new com.lowagie.text.List(
                    com.lowagie.text.List.UNORDERED, 12f);
            consequences.add(new com.lowagie.text.ListItem(
                    "Late-payment penalties will continue to accrue on the outstanding balance.",
                    body));
            consequences.add(new com.lowagie.text.ListItem(
                    "The SACCO may call upon the guarantors named below to settle the outstanding balance.",
                    body));
            consequences.add(new com.lowagie.text.ListItem(
                    "Collateral pledged against the loan may be realized in accordance with the loan agreement.",
                    body));
            consequences.add(new com.lowagie.text.ListItem(
                    "The account may be referred to legal counsel and to a credit reference bureau.",
                    body));
            consequences.setListSymbol(new com.lowagie.text.Chunk("•", bodyBold));
            document.add(consequences);

            if (guarantors != null && !guarantors.isEmpty()) {
                document.add(sectionHeading("GUARANTORS", heading));
                PdfPTable guarantorTable = new PdfPTable(new float[]{2.2f, 1.4f, 1.2f});
                guarantorTable.setWidthPercentage(100);
                guarantorTable.setSpacingAfter(8f);
                addHeader(guarantorTable, tableHeader, "Guarantor", "Guaranteed amount", "Status");
                for (LoanGuarantorResponse guarantor : guarantors) {
                    addCell(guarantorTable, tableBody, value(guarantor.getMemberName()), Element.ALIGN_LEFT);
                    addCell(guarantorTable, tableBody, "UGX " + money(guarantor.getGuaranteedAmount()),
                            Element.ALIGN_RIGHT);
                    addCell(guarantorTable, tableBody, value(guarantor.getStatus()), Element.ALIGN_LEFT);
                }
                document.add(guarantorTable);
            }

            document.add(sectionHeading("ISSUED BY", heading));
            Paragraph issued = new Paragraph(
                    "Issued by: " + value(issuedByName)
                            + "\nDesignation: Collections Officer"
                            + "\nSignature: ______________________________"
                            + "\nDate: " + demand.getIssuedAt().toLocalDate().format(DATE_FORMAT),
                    body);
            issued.setSpacingAfter(16f);
            document.add(issued);

            Paragraph seal = new Paragraph(
                    "Document seal (SHA-256): " + value(demand.getDocumentSeal())
                            + "\nThis is a system-generated demand notice. The seal can be reproduced from "
                            + "the collections register and is used to verify the authenticity of this letter.",
                    small);
            seal.setSpacingBefore(10f);
            document.add(seal);

            document.close();
            return output.toByteArray();
        } catch (Exception exception) {
            if (document.isOpen()) {
                document.close();
            }
            throw new IllegalStateException("Could not render the demand notice", exception);
        }
    }

    private String stageTitle(LoanDemand.DemandStage stage) {
        return switch (stage) {
            case FIRST_DEMAND -> "FIRST DEMAND NOTICE";
            case SECOND_DEMAND -> "SECOND DEMAND NOTICE";
            case FINAL_DEMAND -> "FINAL DEMAND NOTICE";
            case GUARANTOR_DEMAND -> "GUARANTOR DEMAND NOTICE";
            case LEGAL_NOTICE -> "LEGAL NOTICE OF DEFAULT";
        };
    }

    private Paragraph sectionHeading(String text, Font font) {
        Paragraph paragraph = new Paragraph(text, font);
        paragraph.setSpacingBefore(10f);
        paragraph.setSpacingAfter(5f);
        return paragraph;
    }

    private PdfPTable twoColumnBlock(String[] pairs, Font body, Font bodyBold) {
        PdfPTable table = new PdfPTable(new float[]{1.3f, 3.7f});
        table.setWidthPercentage(100);
        table.setSpacingAfter(2f);
        for (int index = 0; index < pairs.length; index += 2) {
            PdfPCell label = new PdfPCell(new Phrase(pairs[index], bodyBold));
            label.setBorder(Rectangle.BOTTOM);
            label.setBorderColor(LIGHT_GREY);
            label.setPadding(5f);
            table.addCell(label);
            PdfPCell value = new PdfPCell(new Phrase(pairs[index + 1], body));
            value.setBorder(Rectangle.BOTTOM);
            value.setBorderColor(LIGHT_GREY);
            value.setPadding(5f);
            table.addCell(value);
        }
        return table;
    }

    private PdfPTable arrearsTable(List<LoanScheduleResponse> schedule,
            LoanDemand demand, Font header, Font body, Font bodyBold) {
        PdfPTable table = new PdfPTable(new float[]{0.7f, 1.2f, 0.8f, 1.2f, 1.2f, 1.1f, 1.3f});
        table.setWidthPercentage(100);
        table.setSpacingBefore(4f);
        table.setSpacingAfter(4f);
        addHeader(table, header, "No.", "Due date", "Days", "Principal", "Interest", "Penalty", "Balance");

        for (LoanScheduleResponse row : schedule) {
            if (row.getDaysOverdue() <= 0 || row.getBalance() == null
                    || row.getBalance().signum() <= 0) {
                continue;
            }
            BigDecimal penalty = nonNegative(row.getPenaltyDue())
                    .subtract(nonNegative(row.getPenaltyPaid()))
                    .add(nonNegative(row.getAccruedPenalty()));
            addCell(table, body, String.valueOf(row.getInstallmentNo()), Element.ALIGN_CENTER);
            addCell(table, body, row.getDueDate().format(DATE_FORMAT), Element.ALIGN_LEFT);
            addCell(table, body, String.valueOf(row.getDaysOverdue()), Element.ALIGN_CENTER);
            addCell(table, body, money(row.getPrincipalDue().subtract(nonNegative(row.getPrincipalPaid()))),
                    Element.ALIGN_RIGHT);
            addCell(table, body, money(row.getInterestDue().subtract(nonNegative(row.getInterestPaid()))),
                    Element.ALIGN_RIGHT);
            addCell(table, body, money(nonNegative(penalty)), Element.ALIGN_RIGHT);
            addCell(table, body, money(row.getBalance()), Element.ALIGN_RIGHT);
        }

        PdfPCell totalLabel = new PdfPCell(new Phrase("TOTAL DEMANDED", bodyBold));
        totalLabel.setColspan(3);
        totalLabel.setBackgroundColor(LIGHT_GREY);
        totalLabel.setPadding(6f);
        table.addCell(totalLabel);
        addCell(table, bodyBold, money(demand.getPrincipalArrears()), Element.ALIGN_RIGHT, LIGHT_GREY);
        addCell(table, bodyBold, money(demand.getInterestArrears()), Element.ALIGN_RIGHT, LIGHT_GREY);
        addCell(table, bodyBold, money(demand.getPenaltyArrears()), Element.ALIGN_RIGHT, LIGHT_GREY);
        addCell(table, bodyBold, money(demand.getAmountDemanded()), Element.ALIGN_RIGHT, LIGHT_GREY);
        return table;
    }

    private void addHeader(PdfPTable table, Font font, String... labels) {
        for (String label : labels) {
            PdfPCell cell = new PdfPCell(new Phrase(label, font));
            cell.setBackgroundColor(NAVY);
            cell.setPadding(6f);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            table.addCell(cell);
        }
    }

    private void addCell(PdfPTable table, Font font, String text, int alignment) {
        addCell(table, font, text, alignment, Color.WHITE);
    }

    private void addCell(PdfPTable table, Font font, String text, int alignment, Color background) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5f);
        cell.setHorizontalAlignment(alignment);
        cell.setBackgroundColor(background);
        cell.setBorderColor(LIGHT_GREY);
        table.addCell(cell);
    }

    private BigDecimal nonNegative(BigDecimal value) {
        return value == null || value.signum() <= 0 ? BigDecimal.ZERO : value;
    }

    private String money(BigDecimal value) {
        return MONEY.format(value == null ? BigDecimal.ZERO : value);
    }

    private String value(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}

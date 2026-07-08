package ETI.sgc.payment.receipt;

import ETI.sgc.config.AppConfig;
import ETI.sgc.error.ApiException;
import com.lowagie.text.Document;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.Image;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class PaymentReceiptService {
    private static final DateTimeFormatter FOLDER_DATE = DateTimeFormatter.ofPattern("yyyyMM");
    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Locale CO_LOCALE = Locale.forLanguageTag("es-CO");

    private final Path uploadBasePath;
    private final Path logoPath;

    public PaymentReceiptService(AppConfig config) {
        this.uploadBasePath = Path.of(config.get("UPLOAD_BASE_DIR", "uploads")).toAbsolutePath().normalize();
        this.logoPath = Path.of(config.get("WEPA_RECEIPT_LOGO_PATH", "../WEPA-FRONT/src/assets/logo.PNG")).toAbsolutePath().normalize();
    }

    public String newReceiptNumber(String source, Object id) {
        String cleanSource = sanitizeCode(source == null ? "SYS" : source);
        String cleanId = sanitizeCode(String.valueOf(id == null ? System.nanoTime() : id));
        return "WEPA-RC-" + LocalDate.now().getYear() + "-" + cleanSource + "-" + cleanId;
    }

    public GeneratedReceipt generate(PaymentReceiptData data) {
        if (data == null) {
            throw new ApiException(400, "Datos de recibo requeridos");
        }
        if (data.receiptNumber == null || data.receiptNumber.isBlank()) {
            data.receiptNumber = newReceiptNumber(data.source, System.nanoTime());
        }
        if (data.issuedAt == null) {
            data.issuedAt = LocalDateTime.now();
        }

        String folder = "documentos/recibos/" + LocalDate.now().format(FOLDER_DATE);
        String filename = "recibo_" + sanitizeCode(data.receiptNumber) + ".pdf";
        Path targetDir = uploadBasePath.resolve(folder).normalize();
        Path target = targetDir.resolve(filename).normalize();
        if (!target.startsWith(uploadBasePath)) {
            throw new ApiException(400, "Ruta de recibo invalida");
        }

        try {
            Files.createDirectories(targetDir);
            writePdf(data, target);
            return new GeneratedReceipt(
                    data.receiptNumber,
                    filename,
                    uploadBasePath.relativize(target).toString().replace('\\', '/')
            );
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw new ApiException(500, "No se pudo generar el recibo");
        }
    }

    private void writePdf(PaymentReceiptData data, Path target) throws Exception {
        Document document = new Document(new Rectangle(595, 842), 42, 42, 36, 36);
        PdfWriter.getInstance(document, Files.newOutputStream(target));
        document.open();

        Font title = new Font(Font.HELVETICA, 24, Font.BOLD);
        Font subtitle = new Font(Font.HELVETICA, 11, Font.NORMAL);
        Font label = new Font(Font.HELVETICA, 9, Font.BOLD);
        Font value = new Font(Font.HELVETICA, 11, Font.NORMAL);

        PdfPTable header = new PdfPTable(new float[]{1.1f, 2f});
        header.setWidthPercentage(100);

        PdfPCell logoCell = new PdfPCell();
        logoCell.setBorder(Rectangle.NO_BORDER);
        if (Files.exists(logoPath)) {
            Image logo = Image.getInstance(logoPath.toString());
            logo.scaleToFit(120, 70);
            logoCell.addElement(logo);
        } else {
            logoCell.addElement(new Paragraph("WEPA", title));
        }
        header.addCell(logoCell);

        PdfPCell receiptCell = new PdfPCell();
        receiptCell.setBorder(Rectangle.NO_BORDER);
        Paragraph receiptTitle = new Paragraph("Recibo de pago", title);
        receiptTitle.setAlignment(Element.ALIGN_RIGHT);
        Paragraph receiptMeta = new Paragraph(data.receiptNumber + "\n" + data.issuedAt.format(DISPLAY_DATE), subtitle);
        receiptMeta.setAlignment(Element.ALIGN_RIGHT);
        receiptCell.addElement(receiptTitle);
        receiptCell.addElement(receiptMeta);
        header.addCell(receiptCell);
        document.add(header);
        document.add(new Paragraph(" "));

        PdfPTable details = new PdfPTable(new float[]{1f, 1.7f});
        details.setWidthPercentage(100);
        addRow(details, "Usuario / tercero", text(data.payerName), label, value);
        addRow(details, "Documento", join(text(data.documentType), text(data.documentNumber)), label, value);
        addRow(details, "Concepto", text(data.concept), label, value);
        addRow(details, "Detalle", text(data.description), label, value);
        addRow(details, "Monto pagado", money(data.amount, data.currency), label, value);
        addRow(details, "Referencia", text(data.reference), label, value);
        addRow(details, "Metodo", text(data.method), label, value);
        addRow(details, "Estado", text(data.status), label, value);
        addRow(details, "Origen", text(data.source), label, value);
        addRow(details, "Validado por", text(data.reviewedBy), label, value);
        addRow(details, "Contacto", text(data.contact), label, value);
        if (data.notes != null && !data.notes.isBlank()) {
            addRow(details, "Observaciones", text(data.notes), label, value);
        }
        document.add(details);

        document.add(new Paragraph(" "));
        Paragraph footer = new Paragraph(
                "Recibo generado por WEPA. Este documento conserva trazabilidad del pago y de sus soportes asociados.",
                subtitle
        );
        footer.setAlignment(Element.ALIGN_CENTER);
        document.add(footer);
        document.close();
    }

    private void addRow(PdfPTable table, String leftLabel, String rightValue, Font label, Font value) {
        PdfPCell left = new PdfPCell(new Phrase(leftLabel, label));
        PdfPCell right = new PdfPCell(new Phrase(rightValue == null ? "" : rightValue, value));
        left.setPadding(10);
        right.setPadding(10);
        left.setBackgroundColor(new java.awt.Color(245, 243, 255));
        left.setBorderColor(new java.awt.Color(230, 225, 255));
        right.setBorderColor(new java.awt.Color(230, 225, 255));
        table.addCell(left);
        table.addCell(right);
    }

    private String money(BigDecimal amount, String currency) {
        BigDecimal safeAmount = amount == null ? BigDecimal.ZERO : amount;
        NumberFormat format = NumberFormat.getCurrencyInstance(CO_LOCALE);
        format.setMaximumFractionDigits(0);
        return format.format(safeAmount) + " " + (currency == null || currency.isBlank() ? "COP" : currency);
    }

    private String join(String left, String right) {
        if (left.isBlank()) return right;
        if (right.isBlank()) return left;
        return left + " " + right;
    }

    private String text(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        return "null".equalsIgnoreCase(text) ? "" : text;
    }

    private String sanitizeCode(String value) {
        return value == null ? "SYS" : value.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "_");
    }
}

package com.aguasystem.service;

import com.aguasystem.entity.Configuracao;
import com.aguasystem.entity.Fatura;
import com.itextpdf.text.*;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PdfService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final Font FONTE_TITULO = new Font(Font.FontFamily.HELVETICA, 14, Font.BOLD);
    private static final Font FONTE_SUBTITULO = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL, BaseColor.GRAY);
    private static final Font FONTE_LABEL = new Font(Font.FontFamily.HELVETICA, 9, Font.BOLD);
    private static final Font FONTE_VALOR = new Font(Font.FontFamily.HELVETICA, 9, Font.NORMAL);
    private static final Font FONTE_TOTAL = new Font(Font.FontFamily.HELVETICA, 12, Font.BOLD);

    private final ConfiguracaoService configuracaoService;

    public PdfService(ConfiguracaoService configuracaoService) {
        this.configuracaoService = configuracaoService;
    }

    /**
     * Gera um PDF com UMA UNICA fatura (ocupa a folha inteira).
     * Usado quando o utilizador quer baixar/imprimir apenas uma fatura avulsa.
     */
    public byte[] gerarPdfFaturaUnica(Fatura fatura) {
        try {
            Configuracao config = configuracaoService.obterConfiguracao();
            Document document = new Document(PageSize.A4, 30, 30, 20, 20);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, baos);
            document.open();
            adicionarFatura(document, fatura, config);
            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new RuntimeException("Erro ao gerar PDF da fatura: " + e.getMessage(), e);
        }
    }

    /**
     * Gera um PDF A4 com DUAS FATURAS DE CLIENTES DIFERENTES por folha,
     * uma na metade superior e outra na metade inferior, separadas por uma
     * linha de corte. Reduz o custo de impressao pela metade ao emitir
     * faturas em lote (ex: todas as faturas pendentes do mes).
     *
     * Se a lista tiver um numero impar de faturas, a ultima fica sozinha
     * na metade superior da sua folha.
     */
    public byte[] gerarPdfLote(List<Fatura> faturas) {
        try {
            Configuracao config = configuracaoService.obterConfiguracao();
            Document document = new Document(PageSize.A4, 30, 30, 20, 20);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfWriter.getInstance(document, baos);
            document.open();

            for (int i = 0; i < faturas.size(); i += 2) {
                if (i > 0) {
                    document.newPage();
                }

                adicionarFatura(document, faturas.get(i), config);

                if (i + 1 < faturas.size()) {
                    Paragraph linhaCorte = new Paragraph(
                            "- - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - - -",
                            new Font(Font.FontFamily.HELVETICA, 8, Font.NORMAL, BaseColor.LIGHT_GRAY));
                    linhaCorte.setAlignment(Element.ALIGN_CENTER);
                    linhaCorte.setSpacingBefore(10);
                    linhaCorte.setSpacingAfter(10);
                    document.add(linhaCorte);

                    adicionarFatura(document, faturas.get(i + 1), config);
                }
            }

            document.close();
            return baos.toByteArray();
        } catch (DocumentException e) {
            throw new RuntimeException("Erro ao gerar PDF em lote: " + e.getMessage(), e);
        }
    }

    private void adicionarFatura(Document document, Fatura fatura, Configuracao config)
            throws DocumentException {

        // Cabecalho
        PdfPTable cabecalho = new PdfPTable(2);
        cabecalho.setWidthPercentage(100);
        cabecalho.setWidths(new float[]{3, 1});

        PdfPCell celEmpresa = new PdfPCell();
        celEmpresa.setBorder(Rectangle.NO_BORDER);
        celEmpresa.addElement(new Paragraph(config.getNomeEmpresa(), FONTE_TITULO));
        if (config.getNuit() != null) {
            celEmpresa.addElement(new Paragraph("NUIT: " + config.getNuit(), FONTE_SUBTITULO));
        }
        if (config.getEndereco() != null) {
            celEmpresa.addElement(new Paragraph(config.getEndereco(), FONTE_SUBTITULO));
        }
        if (config.getTelefone() != null) {
            celEmpresa.addElement(new Paragraph("Tel: " + config.getTelefone(), FONTE_SUBTITULO));
        }
        cabecalho.addCell(celEmpresa);

        PdfPCell celFatura = new PdfPCell(new Phrase("FATURA", FONTE_LABEL));
        celFatura.setBorder(Rectangle.NO_BORDER);
        celFatura.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celFatura.setVerticalAlignment(Element.ALIGN_TOP);
        cabecalho.addCell(celFatura);

        document.add(cabecalho);
        document.add(new Paragraph(" "));

        // Dados da fatura e do cliente
        PdfPTable dados = new PdfPTable(2);
        dados.setWidthPercentage(100);

        adicionarLinha(dados, "Numero da Fatura:", fatura.getNumeroFatura());
        adicionarLinha(dados, "Cliente:", fatura.getCliente().getNomeCompleto());
        adicionarLinha(dados, "N.º Contador:", fatura.getCliente().getNumeroContador());
        adicionarLinha(dados, "Endereco:",
                fatura.getCliente().getEndereco() != null ? fatura.getCliente().getEndereco() : "-");
        adicionarLinha(dados, "Mes de Referencia:",
                fatura.getMesReferencia().format(DateTimeFormatter.ofPattern("MM/yyyy")));
        adicionarLinha(dados, "Data de Emissao:", fatura.getDataEmissao().format(FMT));
        adicionarLinha(dados, "Data de Vencimento:", fatura.getDataVencimento().format(FMT));

        document.add(dados);
        document.add(new Paragraph(" "));

        // Tabela de valores
        PdfPTable valores = new PdfPTable(2);
        valores.setWidthPercentage(100);
        valores.setWidths(new float[]{3, 1});

        adicionarLinhaValor(valores, "Consumo (m3)", fatura.getConsumoM3().toString());
        adicionarLinhaValor(valores, "Preco por m3 (MT)", fatura.getPrecoM3Aplicado().toString());
        if (fatura.getTaxaFixaAplicada() != null && fatura.getTaxaFixaAplicada().signum() > 0) {
            adicionarLinhaValor(valores, "Taxa Fixa (MT)", fatura.getTaxaFixaAplicada().toString());
        }
        if (fatura.getMultaAplicada() != null && fatura.getMultaAplicada().signum() > 0) {
            adicionarLinhaValor(valores, "Multa por Atraso (MT)", fatura.getMultaAplicada().toString());
        }
        if (fatura.getSaldoAnteriorAplicado() != null && fatura.getSaldoAnteriorAplicado().signum() > 0) {
            adicionarLinhaValor(valores, "Divida Anterior (MT)", fatura.getSaldoAnteriorAplicado().toString());
        }

        document.add(valores);

        // Total
        PdfPTable totalTable = new PdfPTable(2);
        totalTable.setWidthPercentage(100);
        totalTable.setWidths(new float[]{3, 1});
        totalTable.setSpacingBefore(5);

        PdfPCell labelTotal = new PdfPCell(new Phrase("VALOR TOTAL (MT)", FONTE_TOTAL));
        labelTotal.setBorder(Rectangle.TOP);
        totalTable.addCell(labelTotal);

        PdfPCell valorTotal = new PdfPCell(new Phrase(fatura.getValorTotal().toString(), FONTE_TOTAL));
        valorTotal.setBorder(Rectangle.TOP);
        valorTotal.setHorizontalAlignment(Element.ALIGN_RIGHT);
        totalTable.addCell(valorTotal);

        document.add(totalTable);

        // Status de pagamento
        if (fatura.getValorPago().signum() > 0) {
            document.add(new Paragraph(" "));
            Paragraph pago = new Paragraph(
                    "Valor Pago: " + fatura.getValorPago() + " MT | Saldo: " + fatura.getSaldoDevedor() + " MT",
                    FONTE_VALOR);
            document.add(pago);
        }

        Paragraph status = new Paragraph("Status: " + traduzirStatus(fatura.getStatus()), FONTE_LABEL);
        status.setSpacingBefore(5);
        document.add(status);
    }

    private void adicionarLinha(PdfPTable table, String label, String valor) {
        PdfPCell celLabel = new PdfPCell(new Phrase(label, FONTE_LABEL));
        celLabel.setBorder(Rectangle.NO_BORDER);
        celLabel.setPaddingBottom(3);
        table.addCell(celLabel);

        PdfPCell celValor = new PdfPCell(new Phrase(valor, FONTE_VALOR));
        celValor.setBorder(Rectangle.NO_BORDER);
        celValor.setPaddingBottom(3);
        table.addCell(celValor);
    }

    private void adicionarLinhaValor(PdfPTable table, String label, String valor) {
        PdfPCell celLabel = new PdfPCell(new Phrase(label, FONTE_VALOR));
        celLabel.setBorder(Rectangle.BOTTOM);
        celLabel.setBorderColor(BaseColor.LIGHT_GRAY);
        celLabel.setPaddingBottom(3);
        table.addCell(celLabel);

        PdfPCell celValor = new PdfPCell(new Phrase(valor, FONTE_VALOR));
        celValor.setBorder(Rectangle.BOTTOM);
        celValor.setBorderColor(BaseColor.LIGHT_GRAY);
        celValor.setHorizontalAlignment(Element.ALIGN_RIGHT);
        celValor.setPaddingBottom(3);
        table.addCell(celValor);
    }

    private String traduzirStatus(Fatura.StatusFatura status) {
        return switch (status) {
            case PENDENTE -> "Pendente";
            case PARCIALMENTE_PAGA -> "Parcialmente Paga";
            case PAGA -> "Paga";
            case VENCIDA -> "Vencida";
            case CANCELADA -> "Cancelada";
            case TRANSFERIDA -> "Transferida para fatura mais recente";
        };
    }
}

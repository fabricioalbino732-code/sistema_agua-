package com.aguasystem.service;

import com.aguasystem.entity.Cliente;
import com.aguasystem.entity.Fatura;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Gera ficheiros Excel (.xlsx) a partir dos dados do sistema, usando a
 * biblioteca Apache POI. Cada metodo publico corresponde a um relatorio
 * exportavel diferente.
 */
@Service
public class ExcelService {

    private static final DateTimeFormatter FMT_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FMT_MES = DateTimeFormatter.ofPattern("MM/yyyy");

    public byte[] gerarExcelClientes(List<Cliente> clientes) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Clientes");
            CellStyle estiloCabecalho = criarEstiloCabecalho(workbook);

            String[] cabecalhos = {"Nome", "N. Contador", "Bairro", "Endereco", "Telefone",
                    "Email", "Tipo", "Status", "Data Ligacao"};
            criarLinhaCabecalho(sheet, cabecalhos, estiloCabecalho);

            int linha = 1;
            for (Cliente c : clientes) {
                Row row = sheet.createRow(linha++);
                row.createCell(0).setCellValue(c.getNomeCompleto());
                row.createCell(1).setCellValue(c.getNumeroContador());
                row.createCell(2).setCellValue(nvl(c.getBairro()));
                row.createCell(3).setCellValue(nvl(c.getEndereco()));
                row.createCell(4).setCellValue(nvl(c.getTelefone()));
                row.createCell(5).setCellValue(nvl(c.getEmail()));
                row.createCell(6).setCellValue(c.getTipoCliente().name());
                row.createCell(7).setCellValue(Boolean.TRUE.equals(c.getAtivo()) ? "Ativo" : "Inativo");
                row.createCell(8).setCellValue(c.getDataLigacao() != null ? c.getDataLigacao().format(FMT_DATA) : "");
            }

            autoAjustarColunas(sheet, cabecalhos.length);
            return converterParaBytes(workbook);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar Excel de clientes: " + e.getMessage(), e);
        }
    }

    public byte[] gerarExcelFaturas(List<Fatura> faturas) {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Faturas");
            CellStyle estiloCabecalho = criarEstiloCabecalho(workbook);

            String[] cabecalhos = {"N. Fatura", "Cliente", "N. Contador", "Mes Referencia", "Data Emissao",
                    "Data Vencimento", "Consumo (m3)", "Divida Anterior (MT)", "Valor Total (MT)",
                    "Valor Pago (MT)", "Saldo Devedor (MT)", "Status"};
            criarLinhaCabecalho(sheet, cabecalhos, estiloCabecalho);

            int linha = 1;
            for (Fatura f : faturas) {
                Row row = sheet.createRow(linha++);
                row.createCell(0).setCellValue(f.getNumeroFatura());
                row.createCell(1).setCellValue(f.getCliente().getNomeCompleto());
                row.createCell(2).setCellValue(f.getCliente().getNumeroContador());
                row.createCell(3).setCellValue(f.getMesReferencia().format(FMT_MES));
                row.createCell(4).setCellValue(f.getDataEmissao().format(FMT_DATA));
                row.createCell(5).setCellValue(f.getDataVencimento().format(FMT_DATA));
                row.createCell(6).setCellValue(f.getConsumoM3().doubleValue());
                row.createCell(7).setCellValue(
                        f.getSaldoAnteriorAplicado() != null ? f.getSaldoAnteriorAplicado().doubleValue() : 0);
                row.createCell(8).setCellValue(f.getValorTotal().doubleValue());
                // Faturas TRANSFERIDA tem o saldo "quitado" apenas na
                // contabilidade interna (arrastado para a fatura seguinte),
                // nunca pago de facto pelo cliente — mostrar isso como
                // "Valor Pago" enganaria quem usar esta coluna para somar
                // o total realmente recebido.
                double valorPagoReal = f.getStatus() == Fatura.StatusFatura.TRANSFERIDA
                        ? 0
                        : f.getValorPago().doubleValue();
                row.createCell(9).setCellValue(valorPagoReal);
                row.createCell(10).setCellValue(f.getSaldoDevedor().doubleValue());
                row.createCell(11).setCellValue(f.getStatus().name());
            }

            autoAjustarColunas(sheet, cabecalhos.length);
            return converterParaBytes(workbook);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar Excel de faturas: " + e.getMessage(), e);
        }
    }

    public byte[] gerarExcelRelatorioDividas(RelatorioService.RelatorioDividas relatorio) {
        try (Workbook workbook = new XSSFWorkbook()) {
            CellStyle estiloCabecalho = criarEstiloCabecalho(workbook);

            Sheet devedoresSheet = workbook.createSheet("Devedores");
            String[] cabecalhosDevedores = {"Cliente", "N. Contador", "Total Faturado (MT)",
                    "Total Pago (MT)", "Saldo Devedor (MT)"};
            criarLinhaCabecalho(devedoresSheet, cabecalhosDevedores, estiloCabecalho);

            int linha = 1;
            for (RelatorioService.ClienteSaldo c : relatorio.getDevedores()) {
                Row row = devedoresSheet.createRow(linha++);
                row.createCell(0).setCellValue(c.getNomeCompleto());
                row.createCell(1).setCellValue(c.getNumeroContador());
                row.createCell(2).setCellValue(c.getTotalFaturado().doubleValue());
                row.createCell(3).setCellValue(c.getTotalPago().doubleValue());
                row.createCell(4).setCellValue(c.getSaldoDevedor().doubleValue());
            }
            autoAjustarColunas(devedoresSheet, cabecalhosDevedores.length);

            Sheet emDiaSheet = workbook.createSheet("Em Dia");
            String[] cabecalhosEmDia = {"Cliente", "N. Contador", "Total Pago (MT)"};
            criarLinhaCabecalho(emDiaSheet, cabecalhosEmDia, estiloCabecalho);

            linha = 1;
            for (RelatorioService.ClienteSaldo c : relatorio.getClientesEmDia()) {
                Row row = emDiaSheet.createRow(linha++);
                row.createCell(0).setCellValue(c.getNomeCompleto());
                row.createCell(1).setCellValue(c.getNumeroContador());
                row.createCell(2).setCellValue(c.getTotalPago().doubleValue());
            }
            autoAjustarColunas(emDiaSheet, cabecalhosEmDia.length);

            return converterParaBytes(workbook);
        } catch (IOException e) {
            throw new RuntimeException("Erro ao gerar Excel do relatorio de dividas: " + e.getMessage(), e);
        }
    }

    private void criarLinhaCabecalho(Sheet sheet, String[] cabecalhos, CellStyle estilo) {
        Row row = sheet.createRow(0);
        for (int i = 0; i < cabecalhos.length; i++) {
            Cell cell = row.createCell(i);
            cell.setCellValue(cabecalhos[i]);
            cell.setCellStyle(estilo);
        }
    }

    private CellStyle criarEstiloCabecalho(Workbook workbook) {
        CellStyle estilo = workbook.createCellStyle();
        Font fonte = workbook.createFont();
        fonte.setBold(true);
        fonte.setColor(IndexedColors.WHITE.getIndex());
        estilo.setFont(fonte);
        estilo.setFillForegroundColor(IndexedColors.DARK_TEAL.getIndex());
        estilo.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        return estilo;
    }

    private void autoAjustarColunas(Sheet sheet, int numColunas) {
        for (int i = 0; i < numColunas; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    private String nvl(String valor) {
        return valor != null ? valor : "";
    }

    private byte[] converterParaBytes(Workbook workbook) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        workbook.write(baos);
        return baos.toByteArray();
    }
}

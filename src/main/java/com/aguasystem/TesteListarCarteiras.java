package com.aguasystem;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Classe temporária, só para testar a chamada GET /wallets do ZumboPay
 * usando o MESMO java.net.http.HttpClient que o ZumboPayService usa em
 * producao — para confirmar se o problema era mesmo o curl/schannel do
 * Windows, e nao a rede ou o codigo Java.
 *
 * Como correr no Eclipse:
 * 1. Clica com o botao direito neste ficheiro
 * 2. Run As > Java Application
 * 3. Ve o resultado na aba "Console"
 *
 * Depois de confirmares o UUID de cada carteira no JSON impresso, PODES
 * APAGAR esta classe — foi so para diagnostico.
 */
public class TesteListarCarteiras {

    public static void main(String[] args) throws Exception {
        // --- Preenche aqui com as tuas credenciais reais ---
        String apiKey = "zk_live_9fa52064c5635341c359200bd4f7dcbafb9508a48197487c";
        String merchantId = "MCH_6CCA02BE20";
        String baseUrl = "https://zumbopay.com/api/public/v1";
        // ----------------------------------------------------

        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/wallets"))
                .header("Authorization", "Bearer " + apiKey)
                .header("X-Merchant-Id", merchantId)
                .timeout(Duration.ofSeconds(20))
                .GET()
                .build();

        System.out.println("A chamar: GET " + baseUrl + "/wallets ...");

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("Status: " + response.statusCode());
            System.out.println("Corpo da resposta:");
            System.out.println(response.body());
        } catch (Exception e) {
            System.out.println("ERRO ao chamar o ZumboPay:");
            System.out.println("Tipo: " + e.getClass().getName());
            System.out.println("Mensagem: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

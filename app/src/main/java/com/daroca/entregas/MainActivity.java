package com.daroca.entregas;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

public class MainActivity extends Activity {

    private WebView webView;

    private String orderCode = "";
    private String plataforma = "";

    private static final String IFOOD_URL =
            "https://confirmacao-entrega-propria.ifood.com.br/numero-pedido";

    private static final String FOOD99_URL =
            "https://food-b-h5.99app.com/pt-BR/v2/confirmation-entrega";

    private final Handler handler =
            new Handler(Looper.getMainLooper());

    private boolean preenchido = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        webView.setWebChromeClient(
                new WebChromeClient()
        );


        webView.setWebViewClient(
                new WebViewClient() {

                    @Override
                    public void onPageFinished(
                            WebView view,
                            String url
                    ) {

                        super.onPageFinished(
                                view,
                                url
                        );


                        if (orderCode.length() != 8) {
                            return;
                        }


                        /*
                         * =========================
                         * 99FOOD
                         * =========================
                         */

                        if (
                                plataforma.equals("99")
                                &&
                                url != null
                                &&
                                url.contains("99app.com")
                        ) {

                            iniciarTentativas99(
                                    orderCode
                            );

                            return;
                        }


                        /*
                         * =========================
                         * IFOOD
                         * =========================
                         */

                        if (
                                plataforma.equals("ifood")
                                &&
                                url != null
                                &&
                                url.contains("ifood.com.br")
                        ) {

                            iniciarTentativasIfood(
                                    orderCode
                            );
                        }
                    }
                }
        );


        handleIntent(
                getIntent()
        );
    }


    /*
     * =========================================
     * NOVO LINK RECEBIDO
     * =========================================
     */

    @Override
    protected void onNewIntent(
            Intent intent
    ) {

        super.onNewIntent(intent);

        setIntent(intent);

        handleIntent(intent);
    }


    /*
     * =========================================
     * DESCOBRE SE É IFOOD OU 99
     * =========================================
     */

    private void handleIntent(
            Intent intent
    ) {

        handler.removeCallbacksAndMessages(
                null
        );

        orderCode = "";
        plataforma = "";
        preenchido = false;


        Uri data =
                intent.getData();


        if (
                data != null
                &&
                "daroca".equalsIgnoreCase(
                        data.getScheme()
                )
        ) {

            String host =
                    data.getHost();


            /*
             * =============================
             * 99FOOD
             * daroca://99/12345678
             * =============================
             */

            if (
                    host != null
                    &&
                    host.equalsIgnoreCase("99")
            ) {

                plataforma = "99";
            }


            /*
             * =============================
             * IFOOD
             * daroca://ifood/12345678
             * =============================
             */

            else if (
                    host != null
                    &&
                    host.equalsIgnoreCase("ifood")
            ) {

                plataforma = "ifood";
            }


            /*
             * PEGA O CÓDIGO
             */

            if (
                    !data
                            .getPathSegments()
                            .isEmpty()
            ) {

                orderCode =
                        digitsOnly(
                                data
                                        .getPathSegments()
                                        .get(0)
                        );
            }
        }


        /*
         * =================================
         * ABRE A PLATAFORMA CORRETA
         * =================================
         */

        if (
                orderCode.length() == 8
                &&
                plataforma.equals("99")
        ) {

            Toast.makeText(
                    this,
                    "99Food recebido: "
                            + orderCode,
                    Toast.LENGTH_SHORT
            ).show();


            webView.loadUrl(
                    FOOD99_URL
            );

            return;
        }


        if (
                orderCode.length() == 8
                &&
                plataforma.equals("ifood")
        ) {

            Toast.makeText(
                    this,
                    "iFood recebido: "
                            + orderCode,
                    Toast.LENGTH_SHORT
            ).show();


            webView.loadUrl(
                    IFOOD_URL
            );

            return;
        }


        /*
         * CASO O APP SEJA ABERTO
         * PELO ÍCONE
         */

        Toast.makeText(
                this,
                "Abra pelo link de confirmação.",
                Toast.LENGTH_SHORT
        ).show();
    }


    /*
     * =========================================
     * SOMENTE NÚMEROS
     * =========================================
     */

    private String digitsOnly(
            String value
    ) {

        if (value == null) {
            return "";
        }

        return value.replaceAll(
                "\\D",
                ""
        );
    }


    /*
     * =========================================
     * 99FOOD
     * =========================================
     */

    private void iniciarTentativas99(
            final String codigo
    ) {

        preenchido = false;


        for (int i = 0; i <= 20; i++) {

            handler.postDelayed(
                    () ->
                            preencherCodigo99(
                                    codigo
                            ),
                    i * 500L
            );
        }
    }


    private void preencherCodigo99(
            String codigo
    ) {

        if (preenchido) {
            return;
        }


        if (
                codigo == null
                ||
                codigo.length() != 8
        ) {

            return;
        }


        String codigoSeguro =
                codigo
                        .replace("\\", "")
                        .replace("'", "");


        String javascript =

                "(function(){" +

                "var codigo='" +
                codigoSeguro +
                "';" +

                "var campos=" +
                "document.querySelectorAll(" +
                "'.verification-code-input input'" +
                ");" +

                "if(campos.length !== 8){" +
                "return 'AGUARDANDO:' + campos.length;" +
                "}" +

                "for(var i=0;i<8;i++){" +

                "var campo=campos[i];" +
                "var numero=codigo.charAt(i);" +

                "try{" +

                "var setter=" +
                "Object.getOwnPropertyDescriptor(" +
                "window.HTMLInputElement.prototype," +
                "'value'" +
                ").set;" +

                "setter.call(" +
                "campo," +
                "numero" +
                ");" +

                "campo.dispatchEvent(" +
                "new Event(" +
                "'input'," +
                "{bubbles:true}" +
                ")" +
                ");" +

                "campo.dispatchEvent(" +
                "new Event(" +
                "'change'," +
                "{bubbles:true}" +
                ")" +
                ");" +

                "}catch(e){" +

                "return 'ERRO:' + e.message;" +

                "}" +

                "}" +

                "campos[7].focus();" +

                "return 'PREENCHIDO';" +

                "})();";


        webView.evaluateJavascript(
                javascript,

                resultado -> {

                    if (
                            resultado != null
                            &&
                            resultado.contains(
                                    "PREENCHIDO"
                            )
                    ) {

                        preenchido = true;


                        Toast.makeText(
                                MainActivity.this,
                                "99Food preenchida: "
                                        + codigo,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }


    /*
     * =========================================
     * IFOOD
     * =========================================
     */

    private void iniciarTentativasIfood(
            final String codigo
    ) {

        preenchido = false;


        for (int i = 0; i <= 20; i++) {

            handler.postDelayed(
                    () ->
                            preencherCodigoIfood(
                                    codigo
                            ),
                    i * 500L
            );
        }
    }


    private void preencherCodigoIfood(
            String codigo
    ) {

        if (preenchido) {
            return;
        }


        if (
                codigo == null
                ||
                codigo.length() != 8
        ) {

            return;
        }


        String codigoSeguro =
                codigo
                        .replace("\\", "")
                        .replace("'", "");


        /*
         * Este é o seletor que já usamos
         * no iFood:
         *
         * [data-testid^="order-number-input-"]
         */

        String javascript =

                "(function(){" +

                "var codigo='" +
                codigoSeguro +
                "';" +

                "var campos=" +
                "document.querySelectorAll(" +
                "'[data-testid^=\"order-number-input-\"]'" +
                ");" +

                "if(campos.length !== 8){" +
                "return 'AGUARDANDO:' + campos.length;" +
                "}" +

                "for(var i=0;i<8;i++){" +

                "var campo=campos[i];" +
                "var numero=codigo.charAt(i);" +

                "try{" +

                "var setter=" +
                "Object.getOwnPropertyDescriptor(" +
                "window.HTMLInputElement.prototype," +
                "'value'" +
                ").set;" +

                "setter.call(" +
                "campo," +
                "numero" +
                ");" +

                "campo.dispatchEvent(" +
                "new Event(" +
                "'input'," +
                "{bubbles:true}" +
                ")" +
                ");" +

                "campo.dispatchEvent(" +
                "new Event(" +
                "'change'," +
                "{bubbles:true}" +
                ")" +
                ");" +

                "}catch(e){" +

                "return 'ERRO:' + e.message;" +

                "}" +

                "}" +

                "campos[7].focus();" +

                "return 'PREENCHIDO';" +

                "})();";


        webView.evaluateJavascript(
                javascript,

                resultado -> {

                    if (
                            resultado != null
                            &&
                            resultado.contains(
                                    "PREENCHIDO"
                            )
                    ) {

                        preenchido = true;


                        Toast.makeText(
                                MainActivity.this,
                                "iFood preenchido: "
                                        + codigo,
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                }
        );
    }


    /*
     * =========================================
     * FECHAR
     * =========================================
     */

    @Override
    protected void onDestroy() {

        super.onDestroy();

        handler.removeCallbacksAndMessages(
                null
        );
    }
}

package com.daroca.entregas;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class MainActivity extends Activity {

    private static final String API =
        "https://daroca-licencas.darocarestaurante1.workers.dev";

    private static final String IFOOD_URL =
        "https://confirmacao-entrega-propria.ifood.com.br/numero-pedido";

    private static final String FOOD99_URL =
        "https://food-b-h5.99app.com/pt-BR/v2/confirmation-entrega";

    private static final String PREFS = "daroca_licenca_v1";

    private static final int MARROM = Color.rgb(81, 56, 41);
    private static final int BEGE = Color.rgb(245, 238, 227);
    private static final int VERDE = Color.rgb(40, 118, 75);

    private WebView webView;
    private FrameLayout raiz;
    private LinearLayout painel;
    private EditText campoLogin;
    private EditText campoSenha;
    private TextView textoEstado;
    private Button botaoEntrar;

    private final Handler handler =
        new Handler(Looper.getMainLooper());

    private String orderCode = "";
    private String plataforma = "";
    private String token = "";
    private String dispositivoId = "";

    private boolean preenchido = false;
    private boolean autorizado = false;
    private boolean verificando = false;

    private int versaoOperacao = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        raiz = findViewById(android.R.id.content);
        webView = findViewById(R.id.webView);

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);

        webView.setWebChromeClient(new WebChromeClient());

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);

                if (!autorizado || orderCode.length() != 8) {
                    return;
                }

                if (
                    plataforma.equals("99") &&
                    url != null &&
                    url.startsWith("https://food-b-h5.99app.com/")
                ) {
                    iniciarTentativas99(orderCode);
                }

                if (
                    plataforma.equals("ifood") &&
                    url != null &&
                    url.startsWith(
                        "https://confirmacao-entrega-propria.ifood.com.br/"
                    )
                ) {
                    iniciarTentativasIfood(orderCode);
                }
            }
        });

        SharedPreferences prefs =
            getSharedPreferences(PREFS, MODE_PRIVATE);

        token = prefs.getString("token", "");
        dispositivoId = prefs.getString("dispositivo", "");

        if (dispositivoId.isEmpty()) {
            dispositivoId =
                UUID.randomUUID().toString().replace("-", "");

            prefs.edit()
                .putString("dispositivo", dispositivoId)
                .apply();
        }

        webView.setVisibility(View.GONE);

        criarTelaLogin();
        receberLink(getIntent());

        if (!token.isEmpty()) {
            verificarLicenca();
        } else {
            mostrarLogin(
                "Informe seu login e senha para utilizar o DAROÇA APP."
            );
        }
    }

    private GradientDrawable fundo(int cor, int raio) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(cor);
        drawable.setCornerRadius(dp(raio));
        return drawable;
    }

    private int dp(int valor) {
        return (int) (
            valor * getResources().getDisplayMetrics().density
        );
    }

    private void criarTelaLogin() {
        ScrollView rolagem = new ScrollView(this);
        rolagem.setFillViewport(true);
        rolagem.setBackgroundColor(BEGE);
        rolagem.setTag("telaAcesso");

        painel = new LinearLayout(this);
        painel.setOrientation(LinearLayout.VERTICAL);
        painel.setGravity(Gravity.CENTER);
        painel.setPadding(dp(25), dp(35), dp(25), dp(35));

        rolagem.addView(painel);

        TextView titulo = new TextView(this);
        titulo.setText("DAROÇA APP");
        titulo.setTextSize(30);
        titulo.setTextColor(MARROM);
        titulo.setTypeface(null, Typeface.BOLD);
        titulo.setGravity(Gravity.CENTER);

        painel.addView(titulo);

        TextView subtitulo = new TextView(this);
        subtitulo.setText("Acesso do entregador");
        subtitulo.setTextSize(16);
        subtitulo.setTextColor(MARROM);
        subtitulo.setGravity(Gravity.CENTER);

        LinearLayout.LayoutParams subParams =
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );

        subParams.bottomMargin = dp(28);
        painel.addView(subtitulo, subParams);

        campoLogin = new EditText(this);
        campoLogin.setSingleLine(true);
        campoLogin.setHint("Login do motoboy");
        campoLogin.setTextSize(16);
        campoLogin.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        );
        campoLogin.setPadding(
            dp(15), dp(13), dp(15), dp(13)
        );
        campoLogin.setBackground(
            fundo(Color.WHITE, 10)
        );

        LinearLayout.LayoutParams campoParams =
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
            );

        campoParams.bottomMargin = dp(13);
        painel.addView(campoLogin, campoParams);

        campoSenha = new EditText(this);
        campoSenha.setSingleLine(true);
        campoSenha.setHint("Senha");
        campoSenha.setTextSize(16);
        campoSenha.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_VARIATION_PASSWORD
        );
        campoSenha.setPadding(
            dp(15), dp(13), dp(15), dp(13)
        );
        campoSenha.setBackground(
            fundo(Color.WHITE, 10)
        );

        LinearLayout.LayoutParams senhaParams =
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(54)
            );

        senhaParams.bottomMargin = dp(17);
        painel.addView(campoSenha, senhaParams);

        botaoEntrar = new Button(this);
        botaoEntrar.setText("ENTRAR");
        botaoEntrar.setTextColor(Color.WHITE);
        botaoEntrar.setAllCaps(false);
        botaoEntrar.setTextSize(16);
        botaoEntrar.setTypeface(null, Typeface.BOLD);
        botaoEntrar.setBackground(fundo(VERDE, 10));

        painel.addView(
            botaoEntrar,
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(55)
            )
        );

        textoEstado = new TextView(this);
        textoEstado.setTextColor(MARROM);
        textoEstado.setTextSize(14);
        textoEstado.setGravity(Gravity.CENTER);
        textoEstado.setPadding(
            dp(5), dp(20), dp(5), dp(10)
        );

        painel.addView(textoEstado);

        botaoEntrar.setOnClickListener(v -> fazerLogin());

        raiz.addView(
            rolagem,
            new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        );
    }

    private View telaAcesso() {
        return raiz.findViewWithTag("telaAcesso");
    }

    private void mostrarLogin(String mensagem) {
        autorizado = false;
        webView.setVisibility(View.GONE);
        telaAcesso().setVisibility(View.VISIBLE);

        campoLogin.setVisibility(View.VISIBLE);
        campoSenha.setVisibility(View.VISIBLE);
        botaoEntrar.setVisibility(View.VISIBLE);

        textoEstado.setText(mensagem);
    }

    private void mostrarCarregando(String mensagem) {
        webView.setVisibility(View.GONE);
        telaAcesso().setVisibility(View.VISIBLE);

        campoLogin.setVisibility(View.GONE);
        campoSenha.setVisibility(View.GONE);
        botaoEntrar.setVisibility(View.GONE);

        textoEstado.setText(mensagem);
    }

    private void mostrarAguardandoLink() {
        webView.setVisibility(View.GONE);
        telaAcesso().setVisibility(View.VISIBLE);

        campoLogin.setVisibility(View.GONE);
        campoSenha.setVisibility(View.GONE);
        botaoEntrar.setVisibility(View.GONE);

        textoEstado.setText(
            "Acesso autorizado!\n\n" +
            "Agora abra um link de confirmação " +
            "do iFood ou da 99Food."
        );
    }

    private void guardarToken(String novoToken) {
        token = novoToken;

        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putString("token", novoToken)
            .apply();
    }

    private void limparToken() {
        token = "";
        autorizado = false;

        getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .remove("token")
            .apply();
    }

    private static class ResultadoAPI {
        int status;
        JSONObject dados;
        String erro;
    }

    private ResultadoAPI chamarAPI(
        String caminho,
        String metodo,
        JSONObject corpo,
        String autorizacao
    ) {
        ResultadoAPI resultado = new ResultadoAPI();
        HttpURLConnection conexao = null;

        try {
            URL url = new URL(API + caminho);

            conexao = (HttpURLConnection) url.openConnection();
            conexao.setRequestMethod(metodo);
            conexao.setConnectTimeout(12000);
            conexao.setReadTimeout(12000);
            conexao.setUseCaches(false);
            conexao.setRequestProperty(
                "Accept", "application/json"
            );

            if (autorizacao != null && !autorizacao.isEmpty()) {
                conexao.setRequestProperty(
                    "Authorization",
                    "Bearer " + autorizacao
                );
            }

            if (corpo != null) {
                conexao.setDoOutput(true);
                conexao.setRequestProperty(
                    "Content-Type", "application/json"
                );

                byte[] bytes = corpo.toString().getBytes(
                    StandardCharsets.UTF_8
                );

                try (OutputStream saida =
                         conexao.getOutputStream()) {
                    saida.write(bytes);
                }
            }

            resultado.status = conexao.getResponseCode();

            InputStream entrada =
                resultado.status >= 400
                    ? conexao.getErrorStream()
                    : conexao.getInputStream();

            if (entrada == null) {
                resultado.erro = "Servidor sem resposta.";
                return resultado;
            }

            StringBuilder texto = new StringBuilder();

            try (
                BufferedReader leitor = new BufferedReader(
                    new InputStreamReader(
                        entrada, StandardCharsets.UTF_8
                    )
                )
            ) {
                String linha;

                while ((linha = leitor.readLine()) != null) {
                    texto.append(linha);
                }
            }

            resultado.dados = new JSONObject(texto.toString());

            if (
                resultado.status >= 400 ||
                !resultado.dados.optBoolean("sucesso", false)
            ) {
                resultado.erro = resultado.dados.optString(
                    "erro", "Acesso não autorizado."
                );
            }

        } catch (Exception e) {
            resultado.erro =
                "Não foi possível conectar ao servidor. " +
                "Verifique a internet.";
        } finally {
            if (conexao != null) {
                conexao.disconnect();
            }
        }

        return resultado;
    }

    private void fazerLogin() {
        if (verificando) return;

        String login = campoLogin.getText()
            .toString().trim().toLowerCase();

        String senha = campoSenha.getText().toString();

        if (login.isEmpty() || senha.isEmpty()) {
            textoEstado.setText(
                "Informe o login e a senha."
            );
            return;
        }

        verificando = true;
        final int operacao = ++versaoOperacao;

        botaoEntrar.setEnabled(false);
        textoEstado.setText("Verificando acesso...");

        new Thread(() -> {
            ResultadoAPI resposta;

            try {
                JSONObject dados = new JSONObject();
                dados.put("login", login);
                dados.put("senha", senha);
                dados.put("dispositivo_id", dispositivoId);

                resposta = chamarAPI(
                    "/motoboy/login",
                    "POST",
                    dados,
                    null
                );

            } catch (Exception e) {
                resposta = new ResultadoAPI();
                resposta.erro = "Erro ao preparar o login.";
            }

            final ResultadoAPI resultado = resposta;

            runOnUiThread(() -> {
                if (operacao != versaoOperacao) return;

                verificando = false;
                botaoEntrar.setEnabled(true);

                if (resultado.erro != null) {
                    textoEstado.setText(resultado.erro);
                    return;
                }

                String novoToken = resultado.dados.optString(
                    "token", ""
                );

                if (novoToken.isEmpty()) {
                    textoEstado.setText(
                        "O servidor não enviou uma sessão."
                    );
                    return;
                }

                guardarToken(novoToken);
                campoSenha.setText("");

                autorizado = true;
                continuarAposAutorizacao();
            });

        }).start();
    }

    private void verificarLicenca() {
        if (token.isEmpty()) {
            mostrarLogin("Faça login para continuar.");
            return;
        }

        final String tokenAtual = token;
        final int operacao = ++versaoOperacao;

        verificando = true;
        autorizado = false;

        handler.removeCallbacksAndMessages(null);
        mostrarCarregando("Verificando sua licença...");

        new Thread(() -> {
            ResultadoAPI resultado = chamarAPI(
                "/motoboy/verificar",
                "GET",
                null,
                tokenAtual
            );

            runOnUiThread(() -> {
                if (operacao != versaoOperacao) return;

                verificando = false;

                if (resultado.erro != null) {
                    if (
                        resultado.status == 401 ||
                        resultado.status == 403
                    ) {
                        limparToken();
                        mostrarLogin(resultado.erro);
                    } else {
                        mostrarFalhaConexao();
                    }
                    return;
                }

                if (
                    resultado.dados == null ||
                    !resultado.dados.optBoolean(
                        "autorizado", false
                    )
                ) {
                    limparToken();
                    mostrarLogin(
                        "Sua licença não está autorizada."
                    );
                    return;
                }

                autorizado = true;
                continuarAposAutorizacao();
            });
        }).start();
    }

    private void mostrarFalhaConexao() {
        mostrarCarregando(
            "Não foi possível verificar a licença.\n\n" +
            "Conecte-se à internet e tente novamente."
        );

        Button tentar = new Button(this);
        tentar.setText("TENTAR NOVAMENTE");
        tentar.setTextColor(Color.WHITE);
        tentar.setBackground(fundo(VERDE, 10));

        View anterior = painel.findViewWithTag("tentar");
        if (anterior != null) painel.removeView(anterior);

        tentar.setTag("tentar");

        painel.addView(
            tentar,
            new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(55)
            )
        );

        tentar.setOnClickListener(v -> {
            painel.removeView(tentar);
            verificarLicenca();
        });
    }

    private void continuarAposAutorizacao() {
        View tentar = painel.findViewWithTag("tentar");
        if (tentar != null) painel.removeView(tentar);

        if (
            orderCode.length() == 8 &&
            (plataforma.equals("99") ||
             plataforma.equals("ifood"))
        ) {
            abrirConfirmacao();
        } else {
            mostrarAguardandoLink();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        receberLink(intent);

        if (!token.isEmpty()) {
            verificarLicenca();
        } else {
            mostrarLogin(
                "Faça login para abrir esta confirmação."
            );
        }
    }

    private void receberLink(Intent intent) {
        handler.removeCallbacksAndMessages(null);

        orderCode = "";
        plataforma = "";
        preenchido = false;
        autorizado = false;

        if (intent == null) return;

        Uri data = intent.getData();
        if (data == null) return;

        String esquema = data.getScheme();
        String host = data.getHost();

        if (
            esquema != null &&
            esquema.equalsIgnoreCase("daroca")
        ) {
            if ("99".equalsIgnoreCase(host)) {
                plataforma = "99";
            } else if ("ifood".equalsIgnoreCase(host)) {
                plataforma = "ifood";
            }

            if (!data.getPathSegments().isEmpty()) {
                orderCode = digitsOnly(
                    data.getPathSegments().get(0)
                );
            }
        }

        if (
            "https".equalsIgnoreCase(esquema) &&
            "stunning-hamster-387b55.netlify.app"
                .equalsIgnoreCase(host)
        ) {
            if (
                data.getPathSegments().size() >= 2 &&
                "ifood".equalsIgnoreCase(
                    data.getPathSegments().get(0)
                )
            ) {
                plataforma = "ifood";
                orderCode = digitsOnly(
                    data.getPathSegments().get(1)
                );
            }
        }

        if (orderCode.length() != 8) {
            orderCode = "";
            plataforma = "";
        }
    }

    private String digitsOnly(String valor) {
        if (valor == null) return "";
        return valor.replaceAll("\\D", "");
    }

    private void abrirConfirmacao() {
        if (!autorizado) return;

        if (orderCode.length() != 8) {
            mostrarAguardandoLink();
            return;
        }

        preenchido = false;
        handler.removeCallbacksAndMessages(null);

        telaAcesso().setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);

        if (plataforma.equals("99")) {
            Toast.makeText(
                this,
                "99Food recebido: " + orderCode,
                Toast.LENGTH_SHORT
            ).show();

            webView.loadUrl(FOOD99_URL);
        } else if (plataforma.equals("ifood")) {
            Toast.makeText(
                this,
                "iFood recebido: " + orderCode,
                Toast.LENGTH_SHORT
            ).show();

            webView.loadUrl(IFOOD_URL);
        }
    }

    /* PREENCHIMENTO AUTOMÁTICO 99FOOD */

    private void iniciarTentativas99(final String codigo) {
        preenchido = false;

        for (int i = 0; i <= 20; i++) {
            handler.postDelayed(
                () -> preencherCodigo99(codigo),
                i * 500L
            );
        }
    }

    private void preencherCodigo99(String codigo) {
        if (
            !autorizado ||
            preenchido ||
            codigo == null ||
            codigo.length() != 8
        ) {
            return;
        }

        String javascript =
            "(function(){" +
            "var codigo='" + codigo + "';" +
            "var campos=document.querySelectorAll(" +
            "'.verification-code-input input');" +
            "if(campos.length!==8)return 'AGUARDANDO';" +
            "for(var i=0;i<8;i++){" +
            "var campo=campos[i];" +
            "var setter=Object.getOwnPropertyDescriptor(" +
            "window.HTMLInputElement.prototype,'value').set;" +
            "setter.call(campo,codigo.charAt(i));" +
            "campo.dispatchEvent(new Event('input'," +
            "{bubbles:true}));" +
            "campo.dispatchEvent(new Event('change'," +
            "{bubbles:true}));" +
            "}" +
            "campos[7].focus();" +
            "return 'PREENCHIDO';" +
            "})();";

        webView.evaluateJavascript(
            javascript,
            resultado -> {
                if (
                    resultado != null &&
                    resultado.contains("PREENCHIDO")
                ) {
                    preenchido = true;

                    Toast.makeText(
                        MainActivity.this,
                        "99Food preenchida: " + codigo,
                        Toast.LENGTH_SHORT
                    ).show();
                }
            }
        );
    }

    /* PREENCHIMENTO AUTOMÁTICO IFOOD */

    private void iniciarTentativasIfood(final String codigo) {
        preenchido = false;

        for (int i = 0; i <= 20; i++) {
            handler.postDelayed(
                () -> preencherCodigoIfood(codigo),
                i * 500L
            );
        }
    }

    private void preencherCodigoIfood(String codigo) {
        if (
            !autorizado ||
            preenchido ||
            codigo == null ||
            codigo.length() != 8
        ) {
            return;
        }

        String javascript =
            "(function(){" +
            "var codigo='" + codigo + "';" +
            "var campos=document.querySelectorAll(" +
            "'[data-testid^=\"order-number-input-\"]');" +
            "if(campos.length!==8)return 'AGUARDANDO';" +
            "for(var i=0;i<8;i++){" +
            "var campo=campos[i];" +
            "var setter=Object.getOwnPropertyDescriptor(" +
            "window.HTMLInputElement.prototype,'value').set;" +
            "setter.call(campo,codigo.charAt(i));" +
            "campo.dispatchEvent(new Event('input'," +
            "{bubbles:true}));" +
            "campo.dispatchEvent(new Event('change'," +
            "{bubbles:true}));" +
            "}" +
            "campos[7].focus();" +
            "return 'PREENCHIDO';" +
            "})();";

        webView.evaluateJavascript(
            javascript,
            resultado -> {
                if (
                    resultado != null &&
                    resultado.contains("PREENCHIDO")
                ) {
                    preenchido = true;

                    Toast.makeText(
                        MainActivity.this,
                        "iFood preenchido: " + codigo,
                        Toast.LENGTH_SHORT
                    ).show();
                }
            }
        );
    }

    @Override
    public void onBackPressed() {
        if (
            webView.getVisibility() == View.VISIBLE &&
            webView.canGoBack()
        ) {
            webView.goBack();
        } else if (
            webView.getVisibility() == View.VISIBLE
        ) {
            handler.removeCallbacksAndMessages(null);
            orderCode = "";
            plataforma = "";
            webView.loadUrl("about:blank");
            mostrarAguardandoLink();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        webView.destroy();
        super.onDestroy();
    }
}

package com.daroca.entregas;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.WebViewAssetLoader;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final String API =
        "https://daroca-licencas.darocarestaurante1.workers.dev";

    private static final String IFOOD_URL =
        "https://confirmacao-entrega-propria.ifood.com.br/numero-pedido";

    private static final String FOOD99_URL =
        "https://food-b-h5.99app.com/pt-BR/v2/confirmation-entrega";

    private static final String HOME_URL =
        "https://appassets.androidplatform.net/assets/leitor.html";

    private static final String PREFS = "daroca_licenca_v1";
    private static final int CAMERA_REQUEST_CODE = 100;

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

    private PermissionRequest cameraRequest;
    private TextRecognizer recognizer;

    private final Handler handler =
        new Handler(Looper.getMainLooper());

    private String orderCode = "";
    private String plataforma = "";
    private String token = "";
    private String dispositivoId = "";

    private boolean preenchido = false;
    private boolean autorizado = false;
    private boolean verificando = false;
    private boolean bridgeAtiva = false;

    private int versaoOperacao = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        raiz = findViewById(android.R.id.content);
        webView = findViewById(R.id.webView);

        recognizer = TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        );

        WebViewAssetLoader assetLoader =
            new WebViewAssetLoader.Builder()
                .addPathHandler(
                    "/assets/",
                    new WebViewAssetLoader.AssetsPathHandler(this)
                )
                .build();

        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.getSettings().setAllowFileAccess(false);
        webView.getSettings().setAllowContentAccess(false);

        webView.setWebViewClient(new WebViewClient() {

            @Override
            public WebResourceResponse shouldInterceptRequest(
                WebView view,
                WebResourceRequest request
            ) {
                return assetLoader.shouldInterceptRequest(
                    request.getUrl()
                );
            }

            @Override
            public void onPageFinished(
                WebView view,
                String url
            ) {
                super.onPageFinished(view, url);

                if (!autorizado || orderCode.length() != 8) {
                    return;
                }

                if (
                    "ifood".equals(plataforma) &&
                    url != null &&
                    url.startsWith(
                        "https://confirmacao-entrega-propria.ifood.com.br/"
                    )
                ) {
                    iniciarTentativasPreenchimento(orderCode);
                }

                if (
                    "99".equals(plataforma) &&
                    url != null &&
                    url.startsWith(
                        "https://food-b-h5.99app.com/"
                    )
                ) {
                    iniciarTentativasPreenchimento(orderCode);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {

            @Override
            public void onPermissionRequest(
                PermissionRequest request
            ) {
                runOnUiThread(() -> {

                    if (
                        !autorizado ||
                        !estaNaTelaInicial() ||
                        !"https".equals(
                            request.getOrigin().getScheme()
                        ) ||
                        !"appassets.androidplatform.net".equals(
                            request.getOrigin().getHost()
                        )
                    ) {
                        request.deny();
                        return;
                    }

                    for (String recurso : request.getResources()) {
                        if (
                            !PermissionRequest.RESOURCE_VIDEO_CAPTURE
                                .equals(recurso)
                        ) {
                            request.deny();
                            return;
                        }
                    }

                    if (
                        Build.VERSION.SDK_INT >= 23 &&
                        checkSelfPermission(
                            Manifest.permission.CAMERA
                        ) != PackageManager.PERMISSION_GRANTED
                    ) {
                        cameraRequest = request;

                        requestPermissions(
                            new String[]{
                                Manifest.permission.CAMERA
                            },
                            CAMERA_REQUEST_CODE
                        );

                    } else {
                        request.grant(
                            new String[]{
                                PermissionRequest.RESOURCE_VIDEO_CAPTURE
                            }
                        );
                    }
                });
            }
        });

        SharedPreferences prefs =
            getSharedPreferences(PREFS, MODE_PRIVATE);

        token = prefs.getString("token", "");
        dispositivoId = prefs.getString("dispositivo", "");

        if (dispositivoId.isEmpty()) {

            dispositivoId =
                UUID.randomUUID()
                    .toString()
                    .replace("-", "");

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
            valor *
            getResources().getDisplayMetrics().density
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

        painel.setPadding(
            dp(25),
            dp(35),
            dp(25),
            dp(35)
        );

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
            dp(15),
            dp(13),
            dp(15),
            dp(13)
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
            dp(15),
            dp(13),
            dp(15),
            dp(13)
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
        botaoEntrar.setBackground(
            fundo(VERDE, 10)
        );

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
            dp(5),
            dp(20),
            dp(5),
            dp(10)
        );

        painel.addView(textoEstado);

        botaoEntrar.setOnClickListener(
            v -> fazerLogin()
        );

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

        if (bridgeAtiva) {
            webView.removeJavascriptInterface(
                "DaRocaAndroid"
            );
            bridgeAtiva = false;
        }

        webView.loadUrl("about:blank");
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

    private void mostrarLeitor() {

        if (!autorizado) return;

        handler.removeCallbacksAndMessages(null);

        plataforma = "";
        orderCode = "";
        preenchido = false;

        if (!bridgeAtiva) {
            webView.addJavascriptInterface(
                new AndroidBridge(),
                "DaRocaAndroid"
            );

            bridgeAtiva = true;
        }

        telaAcesso().setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);

        webView.loadUrl(HOME_URL);
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

            conexao =
                (HttpURLConnection) url.openConnection();

            conexao.setRequestMethod(metodo);
            conexao.setConnectTimeout(12000);
            conexao.setReadTimeout(12000);
            conexao.setUseCaches(false);

            conexao.setRequestProperty(
                "Accept",
                "application/json"
            );

            if (
                autorizacao != null &&
                !autorizacao.isEmpty()
            ) {
                conexao.setRequestProperty(
                    "Authorization",
                    "Bearer " + autorizacao
                );
            }

            if (corpo != null) {

                conexao.setDoOutput(true);

                conexao.setRequestProperty(
                    "Content-Type",
                    "application/json"
                );

                byte[] bytes =
                    corpo.toString().getBytes(
                        StandardCharsets.UTF_8
                    );

                try (
                    OutputStream saida =
                        conexao.getOutputStream()
                ) {
                    saida.write(bytes);
                }
            }

            resultado.status =
                conexao.getResponseCode();

            InputStream entrada =
                resultado.status >= 400
                    ? conexao.getErrorStream()
                    : conexao.getInputStream();

            if (entrada == null) {
                resultado.erro =
                    "Servidor sem resposta.";
                return resultado;
            }

            StringBuilder texto =
                new StringBuilder();

            try (
                BufferedReader leitor =
                    new BufferedReader(
                        new InputStreamReader(
                            entrada,
                            StandardCharsets.UTF_8
                        )
                    )
            ) {

                String linha;

                while (
                    (linha = leitor.readLine()) != null
                ) {
                    texto.append(linha);
                }
            }

            resultado.dados =
                new JSONObject(texto.toString());

            if (
                resultado.status >= 400 ||
                !resultado.dados.optBoolean(
                    "sucesso",
                    false
                )
            ) {
                resultado.erro =
                    resultado.dados.optString(
                        "erro",
                        "Acesso não autorizado."
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

        String login =
            campoLogin.getText()
                .toString()
                .trim()
                .toLowerCase(Locale.ROOT);

        String senha =
            campoSenha.getText().toString();

        if (
            login.isEmpty() ||
            senha.isEmpty()
        ) {
            textoEstado.setText(
                "Informe o login e a senha."
            );
            return;
        }

        verificando = true;

        final int operacao =
            ++versaoOperacao;

        botaoEntrar.setEnabled(false);
        textoEstado.setText(
            "Verificando acesso..."
        );

        new Thread(() -> {

            ResultadoAPI resposta;

            try {

                JSONObject dados =
                    new JSONObject();

                dados.put("login", login);
                dados.put("senha", senha);

                dados.put(
                    "dispositivo_id",
                    dispositivoId
                );

                resposta = chamarAPI(
                    "/motoboy/login",
                    "POST",
                    dados,
                    null
                );

            } catch (Exception e) {

                resposta = new ResultadoAPI();
                resposta.erro =
                    "Erro ao preparar o login.";
            }

            final ResultadoAPI resultado =
                resposta;

            runOnUiThread(() -> {

                if (
                    operacao != versaoOperacao
                ) {
                    return;
                }

                verificando = false;
                botaoEntrar.setEnabled(true);

                if (
                    resultado.erro != null
                ) {
                    textoEstado.setText(
                        resultado.erro
                    );
                    return;
                }

                String novoToken =
                    resultado.dados.optString(
                        "token",
                        ""
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
            mostrarLogin(
                "Faça login para continuar."
            );
            return;
        }

        final String tokenAtual = token;
        final int operacao =
            ++versaoOperacao;

        verificando = true;
        autorizado = false;

        handler.removeCallbacksAndMessages(null);

        mostrarCarregando(
            "Verificando sua licença..."
        );

        new Thread(() -> {

            ResultadoAPI resultado =
                chamarAPI(
                    "/motoboy/verificar",
                    "GET",
                    null,
                    tokenAtual
                );

            runOnUiThread(() -> {

                if (
                    operacao != versaoOperacao
                ) {
                    return;
                }

                verificando = false;

                if (
                    resultado.erro != null
                ) {

                    if (
                        resultado.status == 401 ||
                        resultado.status == 403
                    ) {
                        limparToken();

                        mostrarLogin(
                            resultado.erro
                        );

                    } else {
                        mostrarFalhaConexao();
                    }

                    return;
                }

                if (
                    resultado.dados == null ||
                    !resultado.dados.optBoolean(
                        "autorizado",
                        false
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

        tentar.setText(
            "TENTAR NOVAMENTE"
        );

        tentar.setTextColor(Color.WHITE);
        tentar.setBackground(
            fundo(VERDE, 10)
        );

        View anterior =
            painel.findViewWithTag("tentar");

        if (anterior != null) {
            painel.removeView(anterior);
        }

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

        View tentar =
            painel.findViewWithTag("tentar");

        if (tentar != null) {
            painel.removeView(tentar);
        }

        if (
            orderCode.length() == 8 &&
            (
                plataforma.equals("99") ||
                plataforma.equals("ifood")
            )
        ) {
            abrirConfirmacao();

        } else {
            mostrarLeitor();
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

            if (
                "99".equalsIgnoreCase(host)
            ) {
                plataforma = "99";

            } else if (
                "ifood".equalsIgnoreCase(host)
            ) {
                plataforma = "ifood";
            }

            if (
                !data.getPathSegments().isEmpty()
            ) {
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

        return valor.replaceAll(
            "\\D",
            ""
        );
    }

    @Override
    public void onRequestPermissionsResult(
        int requestCode,
        String[] permissions,
        int[] grantResults
    ) {

        super.onRequestPermissionsResult(
            requestCode,
            permissions,
            grantResults
        );

        if (
            requestCode == CAMERA_REQUEST_CODE &&
            cameraRequest != null
        ) {

            if (
                grantResults.length > 0 &&
                grantResults[0] ==
                    PackageManager.PERMISSION_GRANTED
            ) {

                cameraRequest.grant(
                    new String[]{
                        PermissionRequest.RESOURCE_VIDEO_CAPTURE
                    }
                );

            } else {
                cameraRequest.deny();
            }

            cameraRequest = null;
        }
    }

    public class AndroidBridge {

        @JavascriptInterface
        public void reconhecerImagem(
            String base64
        ) {

            runOnUiThread(() -> {

                if (
                    !autorizado ||
                    !estaNaTelaInicial()
                ) {
                    return;
                }

                processarImagem(base64);
            });
        }

        @JavascriptInterface
        public void abrirConfirmacao(
            String tipo,
            String codigo
        ) {

            runOnUiThread(() -> {

                if (
                    !autorizado ||
                    !estaNaTelaInicial()
                ) {
                    return;
                }

                String numero =
                    digitsOnly(codigo);

                if (
                    numero.length() != 8
                ) {
                    return;
                }

                if (
                    !"ifood".equals(tipo) &&
                    !"99".equals(tipo)
                ) {
                    return;
                }

                plataforma = tipo;
                orderCode = numero;
                preenchido = false;

                handler.removeCallbacksAndMessages(null);

                webView.removeJavascriptInterface(
                    "DaRocaAndroid"
                );

                bridgeAtiva = false;

                if ("ifood".equals(tipo)) {
                    webView.loadUrl(IFOOD_URL);

                } else {
                    webView.loadUrl(FOOD99_URL);
                }
            });
        }
    }

    private boolean estaNaTelaInicial() {

        return webView != null &&
            webView.getUrl() != null &&
            webView.getUrl().startsWith(
                HOME_URL
            );
    }

    private void abrirConfirmacao() {

        if (
            !autorizado ||
            orderCode.length() != 8
        ) {
            mostrarLeitor();
            return;
        }

        handler.removeCallbacksAndMessages(null);

        preenchido = false;

        if (bridgeAtiva) {

            webView.removeJavascriptInterface(
                "DaRocaAndroid"
            );

            bridgeAtiva = false;
        }

        telaAcesso().setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);

        if ("99".equals(plataforma)) {
            webView.loadUrl(FOOD99_URL);

        } else {
            webView.loadUrl(IFOOD_URL);
        }
    }

    private void processarImagem(String base64) {

        try {

            String imagemLimpa =
                base64.replaceFirst(
                    "^data:image/[^;]+;base64,",
                    ""
                );

            byte[] bytes =
                Base64.decode(
                    imagemLimpa,
                    Base64.DEFAULT
                );

            Bitmap bitmap =
                BitmapFactory.decodeByteArray(
                    bytes,
                    0,
                    bytes.length
                );

            if (bitmap == null) {

                enviarResultado(
                    "",
                    "Não foi possível decodificar a imagem."
                );

                return;
            }

            InputImage imagem =
                InputImage.fromBitmap(
                    bitmap,
                    0
                );

            recognizer.process(imagem)
                .addOnSuccessListener(resultado -> {

                    String texto =
                        resultado.getText();

                    String codigo =
                        encontrarLocalizador(texto);

                    if (!codigo.isEmpty()) {

                        enviarResultado(
                            codigo,
                            ""
                        );

                    } else {

                        enviarResultado(
                            "",
                            texto == null ||
                                texto.trim().isEmpty()
                                ? "Nenhum texto reconhecido."
                                : texto
                        );
                    }
                })
                .addOnFailureListener(erro -> {

                    enviarResultado(
                        "",
                        "Erro: " +
                            erro.getMessage()
                    );
                });

        } catch (Exception erro) {

            enviarResultado(
                "",
                "Erro ao processar: " +
                    erro.getMessage()
            );
        }
    }

    private String encontrarLocalizador(
        String texto
    ) {

        if (
            texto == null ||
            texto.trim().isEmpty()
        ) {
            return "";
        }

        String normalizado =
            texto.toUpperCase(Locale.ROOT)
                .replace("\r", "\n");

        String[] linhas =
            normalizado.split("\n");

        String codigo =
            buscaTradicional(linhas);

        if (!codigo.isEmpty()) {
            return codigo;
        }

        return buscaFlexivel(linhas);
    }

    private String buscaTradicional(
        String[] linhas
    ) {

        Pattern palavra =
            Pattern.compile(
                "LOCAL[I1L]ZADOR"
            );

        for (
            int i = 0;
            i < linhas.length;
            i++
        ) {

            Matcher encontrado =
                palavra.matcher(linhas[i]);

            if (!encontrado.find()) {
                continue;
            }

            String codigo =
                procurarNumerosProximos(
                    linhas,
                    i,
                    encontrado.end(),
                    false
                );

            if (!codigo.isEmpty()) {
                return codigo;
            }
        }

        return "";
    }

    private String buscaFlexivel(
        String[] linhas
    ) {

        for (
            int i = 0;
            i < linhas.length;
            i++
        ) {

            String linha = linhas[i];

            String comparacao =
                linha
                    .replace('0', 'O')
                    .replace('1', 'I')
                    .replace('5', 'S')
                    .replace('8', 'B')
                    .replaceAll(
                        "[^A-Z]",
                        ""
                    );

            String alvo =
                "LOCALIZADOR";

            if (
                comparacao.contains(alvo) ||
                contemPalavraParecida(
                    comparacao,
                    alvo
                )
            ) {

                String codigo =
                    procurarNumerosProximos(
                        linhas,
                        i,
                        0,
                        true
                    );

                if (!codigo.isEmpty()) {
                    return codigo;
                }
            }
        }

        return "";
    }

    private boolean contemPalavraParecida(
        String texto,
        String alvo
    ) {

        if (
            texto.length() <
            alvo.length() - 1
        ) {
            return false;
        }

        int menor =
            alvo.length() - 1;

        int maior =
            alvo.length() + 1;

        for (
            int tamanho = menor;
            tamanho <= maior;
            tamanho++
        ) {

            for (
                int inicio = 0;
                inicio + tamanho <= texto.length();
                inicio++
            ) {

                String trecho =
                    texto.substring(
                        inicio,
                        inicio + tamanho
                    );

                if (
                    distanciaEdicao(
                        trecho,
                        alvo
                    ) <= 1
                ) {
                    return true;
                }
            }
        }

        return false;
    }

    private int distanciaEdicao(
        String a,
        String b
    ) {

        int[][] dp =
            new int[
                a.length() + 1
            ][
                b.length() + 1
            ];

        for (
            int i = 0;
            i <= a.length();
            i++
        ) {
            dp[i][0] = i;
        }

        for (
            int j = 0;
            j <= b.length();
            j++
        ) {
            dp[0][j] = j;
        }

        for (
            int i = 1;
            i <= a.length();
            i++
        ) {

            for (
                int j = 1;
                j <= b.length();
                j++
            ) {

                int custo =
                    a.charAt(i - 1) ==
                        b.charAt(j - 1)
                        ? 0
                        : 1;

                dp[i][j] =
                    Math.min(
                        Math.min(
                            dp[i - 1][j] + 1,
                            dp[i][j - 1] + 1
                        ),
                        dp[i - 1][j - 1] +
                            custo
                    );
            }
        }

        return dp[
            a.length()
        ][
            b.length()
        ];
    }

    private String procurarNumerosProximos(
        String[] linhas,
        int linhaInicial,
        int posicaoInicial,
        boolean flexivel
    ) {

        List<String> candidatos =
            new ArrayList<>();

        String primeiraLinha =
            linhas[linhaInicial];

        if (
            !flexivel &&
            posicaoInicial <
                primeiraLinha.length()
        ) {

            candidatos.add(
                primeiraLinha.substring(
                    posicaoInicial
                )
            );

        } else if (flexivel) {

            int separador =
                primeiraLinha.indexOf(':');

            if (separador >= 0) {

                candidatos.add(
                    primeiraLinha.substring(
                        separador + 1
                    )
                );
            }
        }

        for (
            int j = linhaInicial + 1;
            j <= linhaInicial + 2 &&
                j < linhas.length;
            j++
        ) {

            candidatos.add(
                linhas[j]
            );
        }

        StringBuilder acumulado =
            new StringBuilder();

        for (String trecho : candidatos) {

            String numeros =
                trecho.replaceAll(
                    "\\D",
                    ""
                );

            if (numeros.isEmpty()) {
                continue;
            }

            if (
                acumulado.length() +
                    numeros.length() > 8
            ) {
                break;
            }

            acumulado.append(numeros);

            if (
                acumulado.length() == 8
            ) {
                return acumulado.toString();
            }
        }

        return "";
    }

    private void enviarResultado(
        String codigo,
        String diagnostico
    ) {

        runOnUiThread(() -> {

            if (
                !autorizado ||
                !estaNaTelaInicial()
            ) {
                return;
            }

            String javascript =
                "window.receberResultadoOCR(" +
                    JSONObject.quote(codigo) +
                    "," +
                    JSONObject.quote(diagnostico) +
                    ");";

            webView.evaluateJavascript(
                javascript,
                null
            );
        });
    }

    private void iniciarTentativasPreenchimento(
        String codigo
    ) {

        handler.removeCallbacksAndMessages(null);

        preenchido = false;

        for (
            int i = 0;
            i <= 20;
            i++
        ) {

            handler.postDelayed(
                () -> preencherCodigo(codigo),
                i * 500L
            );
        }
    }

    private void preencherCodigo(
        String codigo
    ) {

        if (
            !autorizado ||
            preenchido ||
            webView == null ||
            codigo == null ||
            codigo.length() != 8
        ) {
            return;
        }

        String seletor;

        if ("ifood".equals(plataforma)) {

            seletor =
                "[data-testid^=\"order-number-input-\"]";

        } else if (
            "99".equals(plataforma)
        ) {

            seletor =
                ".verification-code-input input";

        } else {
            return;
        }

        String javascript =
            "(function(){" +
            "var codigo=" +
                JSONObject.quote(codigo) + ";" +
            "var campos=document.querySelectorAll(" +
                JSONObject.quote(seletor) + ");" +
            "if(campos.length!==8)return 'AGUARDANDO';" +
            "var setter=Object.getOwnPropertyDescriptor(" +
                "window.HTMLInputElement.prototype,'value').set;" +
            "for(var i=0;i<8;i++){" +
            "setter.call(campos[i],codigo.charAt(i));" +
            "campos[i].dispatchEvent(" +
                "new Event('input',{bubbles:true}));" +
            "campos[i].dispatchEvent(" +
                "new Event('change',{bubbles:true}));" +
            "}" +
            "campos[7].focus();" +
            "return 'PREENCHIDO';" +
            "})();";

        webView.evaluateJavascript(
            javascript,
            resultado -> {

                if (
                    resultado != null &&
                    resultado.contains(
                        "PREENCHIDO"
                    )
                ) {

                    preenchido = true;

                    Toast.makeText(
                        MainActivity.this,
                        (
                            "ifood".equals(plataforma)
                                ? "iFood"
                                : "99Food"
                        ) +
                            " preenchido: " +
                            codigo,
                        Toast.LENGTH_SHORT
                    ).show();
                }
            }
        );
    }

    @Override
    public void onBackPressed() {

        if (autorizado) {
            mostrarLeitor();

        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {

        handler.removeCallbacksAndMessages(null);

        if (cameraRequest != null) {
            cameraRequest.deny();
            cameraRequest = null;
        }

        if (recognizer != null) {
            recognizer.close();
        }

        if (webView != null) {
            webView.destroy();
            webView = null;
        }

        super.onDestroy();
    }
}

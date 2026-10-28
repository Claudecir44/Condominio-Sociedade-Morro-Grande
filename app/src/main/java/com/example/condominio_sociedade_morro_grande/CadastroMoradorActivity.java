package com.cjstudio.condominio_sociedade_morro_grande;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ContaHelper;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.MoradorFirestoreRepository;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.List;

public class CadastroMoradorActivity extends AppCompatActivity {

    private TextView textCodigo;
    private ImageView imageMorador;
    private EditText editNome, editCPF, editEmail, editRua, editNumero, editTelefone, editQuadra, editLote;
    private Button btnSalvar;

    private MoradorFirestoreRepository repository;
    private PainelAnexos painelAnexos;
    private String editarCod = null;
    private Uri fotoUri = null;
    private String savedImagePath = null;

    private CapturaFoto capturaFoto;

    // Inquilino (responsável pelo app quando houver)
    private CheckBox checkInquilino;
    private View camposInquilino;
    private EditText editInquilinoNome, editInquilinoCpf, editInquilinoTelefone;
    // Como estava ao abrir a edição, pra saber se o responsável pelo app mudou
    private String cpfResponsavelAntes;
    private boolean fotoEscolhidaAgora = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_morador);

        textCodigo = findViewById(R.id.textCodigo);
        imageMorador = findViewById(R.id.imageMorador);
        editNome = findViewById(R.id.editNome);
        editCPF = findViewById(R.id.editCPF);
        editEmail = findViewById(R.id.editEmail);
        editRua = findViewById(R.id.editRua);
        editNumero = findViewById(R.id.editNumero);
        editTelefone = findViewById(R.id.editTelefone);
        editQuadra = findViewById(R.id.editQuadra);
        editLote = findViewById(R.id.editLote);
        checkInquilino = findViewById(R.id.checkInquilino);
        camposInquilino = findViewById(R.id.camposInquilino);
        editInquilinoNome = findViewById(R.id.editInquilinoNome);
        editInquilinoCpf = findViewById(R.id.editInquilinoCpf);
        editInquilinoTelefone = findViewById(R.id.editInquilinoTelefone);
        checkInquilino.setOnCheckedChangeListener((b, marcado) ->
                camposInquilino.setVisibility(marcado ? View.VISIBLE : View.GONE));
        btnSalvar = findViewById(R.id.btnSalvar);
        painelAnexos = new PainelAnexos(this, findViewById(R.id.painelAnexos));

        repository = new MoradorFirestoreRepository();

        // Foto do perfil (aparece ao lado do nome na lista); anexos são outra coisa, lá embaixo
        capturaFoto = new CapturaFoto(this, uri -> {
            fotoUri = uri;
            fotoEscolhidaAgora = true;
            imageMorador.setImageURI(uri);
        });
        imageMorador.setOnClickListener(v -> escolherFotoPerfil());
        findViewById(R.id.textDicaFoto).setOnClickListener(v -> escolherFotoPerfil());

        if (getIntent().hasExtra("editarCod")) {
            editarCod = getIntent().getStringExtra("editarCod");
            preencherCampos(editarCod);
        } else {
            textCodigo.setText("Novo morador");
        }

        btnSalvar.setOnClickListener(v -> salvarMorador());
    }

    private void escolherFotoPerfil() {
        new AlertDialog.Builder(this)
                .setTitle("Foto do morador")
                .setItems(new String[]{"Tirar foto", "Escolher no celular"}, (d, qual) -> {
                    if (qual == 0) capturaFoto.tirarFoto();
                    else capturaFoto.escolherDaGaleria();
                })
                .show();
    }

    /**
     * Cadastro sem foto de perfil mas com imagem nos anexos (foto colocada pelo botão errado):
     * oferece passar a primeira imagem anexada para a foto do morador.
     */
    private void oferecerAnexoComoFoto() {
        if (fotoUri != null) return;
        for (String anexo : painelAnexos.todos()) {
            if (!ArquivosStorage.ehImagem(this, anexo)) continue;
            new AlertDialog.Builder(this)
                    .setTitle("Usar como foto do morador?")
                    .setMessage("Este morador não tem foto de perfil, mas tem uma imagem nos anexos. "
                            + "Quer usá-la como foto do morador (ao lado do nome)?")
                    .setPositiveButton("Usar como foto", (d, w) -> {
                        fotoUri = ArquivosStorage.paraUri(anexo);
                        fotoEscolhidaAgora = true;
                        ArquivosStorage.carregarImagem(imageMorador, anexo, android.R.drawable.ic_menu_camera);
                        painelAnexos.remover(anexo);
                        Toast.makeText(this, "Toque em Salvar Morador para confirmar.", Toast.LENGTH_LONG).show();
                    })
                    .setNegativeButton("Não", null)
                    .show();
            return;
        }
    }

    // ==================== MÉTODOS LOCAIS PARA IMAGENS ====================

    private String saveImageToInternalStorage(Uri uri) {
        try {
            String fileName = "morador_" + System.currentTimeMillis() + ".jpg";
            File destFile = new File(getFilesDir(), fileName);

            try (InputStream in = getContentResolver().openInputStream(uri);
                 FileOutputStream out = new FileOutputStream(destFile)) {
                if (in == null) return null;
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    out.write(buffer, 0, bytesRead);
                }
                return destFile.getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private Uri loadImageFromInternalStorage(String imagePath) {
        if (imagePath == null) return null;
        File file = new File(imagePath);
        if (file.exists()) {
            return Uri.fromFile(file);
        }
        return null;
    }

    private boolean deleteImageFile(String imagePath) {
        if (imagePath == null) return false;
        File file = new File(imagePath);
        return file.exists() && file.delete();
    }

    // ==================== FIM DOS MÉTODOS LOCAIS ====================

    private void preencherCampos(String cod) {
        repository.getListaMoradores(new MoradorFirestoreRepository.OnCompleteListener<List<JSONObject>>() {
            @Override
            public void onComplete(List<JSONObject> lista) {
                for (JSONObject morador : lista) {
                    if (morador.optString("cod").equals(cod)) {
                        runOnUiThread(() -> {
                            textCodigo.setText("Código: " + morador.optString("cod"));
                            editNome.setText(morador.optString("nome"));
                            editCPF.setText(morador.optString("cpf"));
                            editEmail.setText(morador.optString("email"));
                            editRua.setText(morador.optString("rua"));
                            editNumero.setText(morador.optString("numero"));
                            editTelefone.setText(morador.optString("telefone"));
                            editQuadra.setText(morador.optString("quadra"));
                            editLote.setText(morador.optString("lote"));
                            checkInquilino.setChecked(morador.optBoolean("temInquilino"));
                            editInquilinoNome.setText(morador.optString("inquilinoNome"));
                            editInquilinoCpf.setText(morador.optString("inquilinoCpf"));
                            editInquilinoTelefone.setText(morador.optString("inquilinoTelefone"));
                            cpfResponsavelAntes = Inquilino.cpfResponsavel(morador);
                            painelAnexos.definir(ListaAnexos.deValor(morador.opt("anexos")));

                            String imagePath = morador.optString("foto");
                            if (!imagePath.isEmpty()) {
                                fotoUri = ArquivosStorage.paraUri(imagePath);
                                savedImagePath = imagePath;
                            }
                            ArquivosStorage.carregarImagem(imageMorador, imagePath,
                                    android.R.drawable.ic_menu_camera);
                            oferecerAnexoComoFoto();
                        });
                        break;
                    }
                }
            }

            @Override
            public void onError(Exception e) {
                runOnUiThread(() -> Toast.makeText(CadastroMoradorActivity.this,
                        "Erro ao carregar morador: " + e.getMessage(), Toast.LENGTH_SHORT).show());
            }
        });
    }

    private void salvarMorador() {
        String nome = editNome.getText().toString().trim();
        String cpf = editCPF.getText().toString().trim();
        String email = editEmail.getText().toString().trim();
        String rua = editRua.getText().toString().trim();
        String numero = editNumero.getText().toString().trim();
        String telefone = editTelefone.getText().toString().trim();
        String quadra = editQuadra.getText().toString().trim().toUpperCase();
        String lote = editLote.getText().toString().trim();

        // Foto é opcional aqui: se o admin não colocar, o morador cadastra a dele no primeiro login

        if (nome.isEmpty()) {
            Toast.makeText(this, "Preencha o nome!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (cpf.isEmpty()) {
            Toast.makeText(this, "Preencha o CPF!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (quadra.isEmpty()) {
            Toast.makeText(this, "Preencha a quadra!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (lote.isEmpty()) {
            Toast.makeText(this, "Preencha o lote!", Toast.LENGTH_SHORT).show();
            return;
        }

        boolean temInquilino = checkInquilino.isChecked();
        String inquilinoNome = editInquilinoNome.getText().toString().trim();
        String inquilinoCpf = editInquilinoCpf.getText().toString().trim();
        String inquilinoTelefone = editInquilinoTelefone.getText().toString().trim();
        if (temInquilino && (inquilinoNome.isEmpty() || inquilinoCpf.isEmpty())) {
            Toast.makeText(this, "Preencha o nome e o CPF do inquilino!", Toast.LENGTH_SHORT).show();
            return;
        }

        // Gera usuario = quadra + lote (ex: A10)
        String usuario = (quadra + lote).toUpperCase();
        // Senha inicial = 6 primeiros dígitos do CPF de quem usa o app: o inquilino, se houver
        // (fica só no Firebase Auth, não no banco)
        String cpfResponsavel = temInquilino ? inquilinoCpf : cpf;
        String senha = ContaHelper.senhaInicialMorador(cpfResponsavel);
        if (senha == null && (editarCod == null || temInquilino)) {
            Toast.makeText(this, "Informe o CPF completo" + (temInquilino ? " do inquilino" : "")
                    + ": a senha inicial são os 6 primeiros dígitos.", Toast.LENGTH_LONG).show();
            return;
        }
        // Na edição: mudou quem usa o app (entrou/saiu inquilino, ou outro CPF)?
        boolean responsavelMudou = editarCod != null && cpfResponsavelAntes != null
                && !cpfResponsavelAntes.replaceAll("[^0-9]", "").equals(cpfResponsavel.replaceAll("[^0-9]", ""));

        // Cria JSON com os dados
        JSONObject morador = new JSONObject();
        try {
            morador.put("nome", nome);
            morador.put("cpf", cpf);
            morador.put("email", email);
            morador.put("rua", rua);
            morador.put("numero", numero);
            morador.put("telefone", telefone);
            morador.put("quadra", quadra);
            morador.put("lote", lote);
            morador.put("temInquilino", temInquilino);
            morador.put("inquilinoNome", temInquilino ? inquilinoNome : "");
            morador.put("inquilinoCpf", temInquilino ? inquilinoCpf : "");
            morador.put("inquilinoTelefone", temInquilino ? inquilinoTelefone : "");
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Erro ao preparar dados: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        btnSalvar.setEnabled(false);
        // Foto vai pro Firebase Storage; o cadastro guarda o link
        // Trocou quem usa o app e não escolheu foto nova: a foto sai, e o novo responsável cadastra
        // a dele no primeiro acesso
        String foto = responsavelMudou && !fotoEscolhidaAgora ? null
                : fotoUri != null ? fotoUri.toString() : null;
        ArquivosStorage.enviarUm(this, "moradores", foto, new ColecaoFirestore.Callback<String>() {
            @Override
            public void onSucesso(String link) {
                try {
                    morador.put("foto", link != null ? link : "");
                } catch (Exception ignored) {
                }
                // Depois da foto, os anexos (documentos, contratos, comprovantes...)
                painelAnexos.enviar("moradores", new ColecaoFirestore.Callback<List<String>>() {
                    @Override
                    public void onSucesso(List<String> links) {
                        try {
                            morador.put("anexos", new org.json.JSONArray(links));
                        } catch (Exception ignored) {
                        }
                        gravarMorador(morador, usuario, senha, temInquilino, responsavelMudou);
                    }

                    @Override
                    public void onErro(Exception e) {
                        btnSalvar.setEnabled(true);
                        Toast.makeText(CadastroMoradorActivity.this,
                                "Erro ao enviar os anexos: " + e.getMessage(), Toast.LENGTH_LONG).show();
                    }
                });
            }

            @Override
            public void onErro(Exception e) {
                btnSalvar.setEnabled(true);
                Toast.makeText(CadastroMoradorActivity.this,
                        "Erro ao enviar a foto: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void gravarMorador(JSONObject morador, String usuario, String senha,
                               boolean temInquilino, boolean responsavelMudou) {
        String deQuem = temInquilino ? "do inquilino" : "do morador";
        if (editarCod != null) {
            // Atualiza
            repository.atualizarMorador(editarCod, morador, new MoradorFirestoreRepository.OnCompleteListener<Void>() {
                @Override
                public void onComplete(Void result) {
                    runOnUiThread(() -> {
                        if (responsavelMudou) {
                            trocarSenhaDoResponsavel(usuario, senha, deQuem);
                        } else {
                            Toast.makeText(CadastroMoradorActivity.this, "Morador atualizado!", Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                }

                @Override
                public void onError(Exception e) {
                    btnSalvar.setEnabled(true);
                    runOnUiThread(() -> Toast.makeText(CadastroMoradorActivity.this,
                            "Erro ao atualizar: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        } else {
            // Insere
            repository.inserirMorador(this, morador, usuario, senha, new MoradorFirestoreRepository.OnCompleteListener<String>() {
                @Override
                public void onComplete(String newId) {
                    runOnUiThread(() -> new AlertDialog.Builder(CadastroMoradorActivity.this)
                            .setTitle("Morador cadastrado!")
                            .setMessage("Passe os dados de acesso " + (temInquilino ? "ao inquilino" : "ao morador")
                                    + ":\n\nUsuário: " + usuario
                                    + "\nSenha: " + senha + " (6 primeiros dígitos do CPF " + deQuem + ")")
                            .setCancelable(false)
                            .setPositiveButton("OK", (d, w) -> finish())
                            .show());
                }

                @Override
                public void onError(Exception e) {
                    btnSalvar.setEnabled(true);
                    runOnUiThread(() -> Toast.makeText(CadastroMoradorActivity.this,
                            "Erro ao cadastrar: " + e.getMessage(), Toast.LENGTH_SHORT).show());
                }
            });
        }
    }

    /**
     * Entrou/saiu inquilino ou mudou o CPF de quem usa o app: a senha passa a ser os 6 primeiros
     * dígitos do CPF do novo responsável. Só o servidor troca a senha de outra conta
     * (Cloud Function definirSenhaResponsavelMorador, que lê o CPF do próprio cadastro).
     */
    private void trocarSenhaDoResponsavel(String usuario, String senha, String deQuem) {
        java.util.Map<String, Object> dados = new java.util.HashMap<>();
        dados.put("moradorUid", editarCod);
        com.google.firebase.functions.FirebaseFunctions.getInstance(AssinaturaRepository.REGIAO_FUNCOES)
                .getHttpsCallable("definirSenhaResponsavelMorador").call(dados)
                .addOnSuccessListener(r -> new AlertDialog.Builder(this)
                        .setTitle("Morador atualizado!")
                        .setMessage("Mudou quem usa o app. Passe os novos dados de acesso:\n\nUsuário: " + usuario
                                + "\nSenha: " + senha + " (6 primeiros dígitos do CPF " + deQuem + ")"
                                + "\n\nNo primeiro acesso ele cadastra a foto.")
                        .setCancelable(false)
                        .setPositiveButton("OK", (d, w) -> finish())
                        .show())
                .addOnFailureListener(e -> new AlertDialog.Builder(this)
                        .setTitle("Dados salvos, senha não trocada")
                        .setMessage("O cadastro foi atualizado, mas não deu pra trocar a senha pelo CPF "
                                + deQuem + ": " + e.getMessage()
                                + "\n\nO acesso continua com a senha antiga até isso ser feito.")
                        .setCancelable(false)
                        .setPositiveButton("OK", (d, w) -> finish())
                        .show());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Limpeza se necessário
    }
}
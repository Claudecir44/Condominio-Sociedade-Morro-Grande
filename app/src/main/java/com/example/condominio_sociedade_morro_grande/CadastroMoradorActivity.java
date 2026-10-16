package com.cjstudio.condominio_sociedade_morro_grande;

import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
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

    private final ActivityResultLauncher<String> selectImageLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    fotoUri = uri;
                    imageMorador.setImageURI(uri);
                }
            });

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
        btnSalvar = findViewById(R.id.btnSalvar);
        painelAnexos = new PainelAnexos(this, findViewById(R.id.painelAnexos));

        repository = new MoradorFirestoreRepository();

        imageMorador.setOnClickListener(v -> selectImageLauncher.launch("image/*"));

        if (getIntent().hasExtra("editarCod")) {
            editarCod = getIntent().getStringExtra("editarCod");
            preencherCampos(editarCod);
        } else {
            textCodigo.setText("Novo morador");
        }

        btnSalvar.setOnClickListener(v -> salvarMorador());
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
                            painelAnexos.definir(ListaAnexos.deValor(morador.opt("anexos")));

                            String imagePath = morador.optString("foto");
                            if (!imagePath.isEmpty()) {
                                fotoUri = ArquivosStorage.paraUri(imagePath);
                                savedImagePath = imagePath;
                            }
                            ArquivosStorage.carregarImagem(imageMorador, imagePath,
                                    android.R.drawable.ic_menu_camera);
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

        // Gera usuario = quadra + lote (ex: A10)
        String usuario = (quadra + lote).toUpperCase();
        // Senha inicial = 6 primeiros dígitos do CPF (fica só no Firebase Auth, não no banco)
        String senha = ContaHelper.senhaInicialMorador(cpf);
        if (editarCod == null && senha == null) {
            Toast.makeText(this, "Informe o CPF completo: a senha inicial são os 6 primeiros dígitos.",
                    Toast.LENGTH_LONG).show();
            return;
        }

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
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Erro ao preparar dados: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        btnSalvar.setEnabled(false);
        // Foto vai pro Firebase Storage; o cadastro guarda o link
        String foto = fotoUri != null ? fotoUri.toString() : null;
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
                        gravarMorador(morador, usuario, senha);
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

    private void gravarMorador(JSONObject morador, String usuario, String senha) {
        if (editarCod != null) {
            // Atualiza
            repository.atualizarMorador(editarCod, morador, new MoradorFirestoreRepository.OnCompleteListener<Void>() {
                @Override
                public void onComplete(Void result) {
                    runOnUiThread(() -> {
                        Toast.makeText(CadastroMoradorActivity.this, "Morador atualizado!", Toast.LENGTH_SHORT).show();
                        finish();
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
                            .setMessage("Passe os dados de acesso ao morador:\n\nUsuário: " + usuario
                                    + "\nSenha: " + senha + " (6 primeiros dígitos do CPF)")
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Limpeza se necessário
    }
}
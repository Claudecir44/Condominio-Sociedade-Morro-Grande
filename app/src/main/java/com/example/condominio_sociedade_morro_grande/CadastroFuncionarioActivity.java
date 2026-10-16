package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.cjstudio.condominio_sociedade_morro_grande.data.FuncionarioDAO;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage;
import com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ColecaoFirestore;
import com.cjstudio.condominio_sociedade_morro_grande.domain.model.Funcionario;


public class CadastroFuncionarioActivity extends AppCompatActivity {

    private EditText etNome, etRua, etNumero, etBairro, etCep, etCidade, etEstado, etPais,
            etTelefone, etEmail, etRG, etCPF, etCargaHoraria, etTurno,
            etHoraEntrada, etHoraSaida, etCargo;
    private Button btnSalvar;
    private ImageView imageFuncionario;
    private Uri imagemSelecionadaUri;
    private String funcionarioId = null;
    private FuncionarioDAO funcionarioDAO;

    private final ActivityResultLauncher<String> selecionarImagemLauncher =
            registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        imagemSelecionadaUri = uri;
                        imageFuncionario.setImageURI(uri);
                    } catch (SecurityException e) {
                        imagemSelecionadaUri = uri;
                        imageFuncionario.setImageURI(uri);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cadastro_funcionario);

        funcionarioDAO = new FuncionarioDAO();

        // Inicializa campos
        etNome = findViewById(R.id.editNomeFuncionario);
        etRua = findViewById(R.id.editRuaFuncionario);
        etNumero = findViewById(R.id.editNumeroFuncionario);
        etBairro = findViewById(R.id.editBairroFuncionario);
        etCep = findViewById(R.id.editCepFuncionario);
        etCidade = findViewById(R.id.editCidadeFuncionario);
        etEstado = findViewById(R.id.editEstadoFuncionario);
        etPais = findViewById(R.id.editPaisFuncionario);
        etTelefone = findViewById(R.id.editTelefoneFuncionario);
        etEmail = findViewById(R.id.editEmailFuncionario);
        etRG = findViewById(R.id.editRGFuncionario);
        etCPF = findViewById(R.id.editCPFFuncionario);
        etCargaHoraria = findViewById(R.id.editCargaHoraria);
        etTurno = findViewById(R.id.editTurno);
        etHoraEntrada = findViewById(R.id.editHoraEntrada);
        etHoraSaida = findViewById(R.id.editHoraSaida);
        etCargo = findViewById(R.id.editCargoFuncionario);

        btnSalvar = findViewById(R.id.buttonSalvarFuncionario);
        imageFuncionario = findViewById(R.id.imageFuncionario);

        configurarCampos();

        imageFuncionario.setOnClickListener(v -> selecionarImagemLauncher.launch("image/*"));

        if (getIntent().hasExtra("funcionarioJSON")) {
            carregarFuncionario(funcionarioDAO.deJson(getIntent().getStringExtra("funcionarioJSON")));
        }

        btnSalvar.setOnClickListener(v -> salvarFuncionario());
    }

    private void configurarCampos() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            etTelefone.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO);
        }
        etTelefone.setAutofillHints("");

        etTelefone.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etEmail.requestFocus();
                return true;
            }
            return false;
        });

        etNome.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etRua.requestFocus();
                return true;
            }
            return false;
        });

        etRua.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etNumero.requestFocus();
                return true;
            }
            return false;
        });

        etNumero.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etBairro.requestFocus();
                return true;
            }
            return false;
        });

        etBairro.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etCep.requestFocus();
                return true;
            }
            return false;
        });

        etCep.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etCidade.requestFocus();
                return true;
            }
            return false;
        });

        etCidade.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etEstado.requestFocus();
                return true;
            }
            return false;
        });

        etEstado.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etPais.requestFocus();
                return true;
            }
            return false;
        });

        etPais.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etTelefone.requestFocus();
                return true;
            }
            return false;
        });

        etEmail.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etRG.requestFocus();
                return true;
            }
            return false;
        });

        etRG.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etCPF.requestFocus();
                return true;
            }
            return false;
        });

        etCPF.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etCargo.requestFocus();
                return true;
            }
            return false;
        });

        etCargo.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etCargaHoraria.requestFocus();
                return true;
            }
            return false;
        });

        etCargaHoraria.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etTurno.requestFocus();
                return true;
            }
            return false;
        });

        etTurno.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etHoraEntrada.requestFocus();
                return true;
            }
            return false;
        });

        etHoraEntrada.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_NEXT) {
                etHoraSaida.requestFocus();
                return true;
            }
            return false;
        });

        etHoraSaida.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                salvarFuncionario();
                return true;
            }
            return false;
        });
    }

    private void carregarFuncionario(Funcionario f) {
        if (f != null) {
            funcionarioId = f.getId();
            etNome.setText(f.getNome());
            etRua.setText(f.getRua());
            etNumero.setText(f.getNumero());
            etBairro.setText(f.getBairro());
            etCep.setText(f.getCep());
            etCidade.setText(f.getCidade());
            etEstado.setText(f.getEstado());
            etPais.setText(f.getPais());
            etTelefone.setText(f.getTelefone());
            etEmail.setText(f.getEmail());
            etRG.setText(f.getRg());
            etCPF.setText(f.getCpf());
            etCargaHoraria.setText(f.getCargaHoraria());
            etTurno.setText(f.getTurno());
            etHoraEntrada.setText(f.getHoraEntrada());
            etHoraSaida.setText(f.getHoraSaida());
            etCargo.setText(f.getCargo());

            if (f.getImagemUri() != null && !f.getImagemUri().isEmpty()) {
                try {
                    imagemSelecionadaUri = Uri.parse(f.getImagemUri());
                    ArquivosStorage.carregarImagem(imageFuncionario, f.getImagemUri(),
                            android.R.drawable.ic_menu_camera);
                } catch (Exception e) {
                    imageFuncionario.setImageResource(android.R.drawable.ic_menu_camera);
                }
            }
        }
    }

    private void salvarFuncionario() {
        String nome = etNome.getText().toString().trim();

        if (nome.isEmpty()) {
            Toast.makeText(this, "Preencha o nome do funcionário!", Toast.LENGTH_SHORT).show();
            etNome.requestFocus();
            return;
        }

        Funcionario funcionario = new Funcionario();
        funcionario.setId(funcionarioId);
        funcionario.setNome(nome);
        funcionario.setRua(etRua.getText().toString().trim());
        funcionario.setNumero(etNumero.getText().toString().trim());
        funcionario.setBairro(etBairro.getText().toString().trim());
        funcionario.setCep(etCep.getText().toString().trim());
        funcionario.setCidade(etCidade.getText().toString().trim());
        funcionario.setEstado(etEstado.getText().toString().trim());
        funcionario.setPais(etPais.getText().toString().trim());
        funcionario.setTelefone(etTelefone.getText().toString().trim());
        funcionario.setEmail(etEmail.getText().toString().trim());
        funcionario.setRg(etRG.getText().toString().trim());
        funcionario.setCpf(etCPF.getText().toString().trim());
        funcionario.setCargaHoraria(etCargaHoraria.getText().toString().trim());
        funcionario.setTurno(etTurno.getText().toString().trim());
        funcionario.setHoraEntrada(etHoraEntrada.getText().toString().trim());
        funcionario.setHoraSaida(etHoraSaida.getText().toString().trim());
        funcionario.setCargo(etCargo.getText().toString().trim());

        btnSalvar.setEnabled(false);
        // Foto vai pro Firebase Storage; o cadastro guarda o link
        String foto = imagemSelecionadaUri != null ? imagemSelecionadaUri.toString() : null;
        ArquivosStorage.enviarUm(this, "funcionarios", foto, new ColecaoFirestore.Callback<String>() {
            @Override
            public void onSucesso(String link) {
                funcionario.setImagemUri(link);
                gravarFuncionario(funcionario);
            }

            @Override
            public void onErro(Exception e) {
                aoFalhar(new Exception("não foi possível enviar a foto (" + e.getMessage() + ")"));
            }
        });
    }

    private void gravarFuncionario(Funcionario funcionario) {
        if (funcionarioId != null) {
            funcionarioDAO.atualizar(funcionario, new ColecaoFirestore.Callback<Void>() {
                @Override
                public void onSucesso(Void resultado) {
                    aoSalvar();
                }

                @Override
                public void onErro(Exception e) {
                    aoFalhar(e);
                }
            });
        } else {
            funcionarioDAO.inserir(funcionario, new ColecaoFirestore.Callback<String>() {
                @Override
                public void onSucesso(String id) {
                    aoSalvar();
                }

                @Override
                public void onErro(Exception e) {
                    aoFalhar(e);
                }
            });
        }
    }

    private void aoSalvar() {
        btnSalvar.setEnabled(true);
        Toast.makeText(this, "Funcionário salvo com sucesso!", Toast.LENGTH_SHORT).show();
        finish();
    }

    private void aoFalhar(Exception e) {
        btnSalvar.setEnabled(true);
        Toast.makeText(this, "Erro ao salvar funcionário: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && funcionarioId == null) {
            hideKeyboard();
        }
    }

    private void hideKeyboard() {
        View view = this.getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }
}
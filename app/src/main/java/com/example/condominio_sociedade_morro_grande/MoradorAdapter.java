package com.cjstudio.condominio_sociedade_morro_grande;

import android.content.Context;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.util.List;

public class MoradorAdapter extends RecyclerView.Adapter<MoradorAdapter.MoradorViewHolder> {

    public interface OnItemClickListener {
        void onEditar(JSONObject morador, int position);
        void onExcluir(JSONObject morador, int position);
        void onAdicionarBoleto(JSONObject morador, String mes);
        void onCriarMes(JSONObject morador, String mes);
        void onMarcarBoletoNaoPago(Boleto boleto, boolean naoPago);
        void onExcluirBoleto(Boleto boleto);
    }

    private Context context;
    private List<JSONObject> moradores;
    private OnItemClickListener listener;
    // Morador de visualização: só vê a lista, sem Editar/Excluir
    private boolean somenteLeitura = false;
    // Códigos dos cards abertos na lista compacta
    private final java.util.Set<String> expandidos = new java.util.HashSet<>();
    // Boletos de cada morador, pelo UID (cod)
    private java.util.Map<String, List<Boleto>> boletos = new java.util.HashMap<>();
    // Meses abertos em "Boletos Morador": continuam abertos quando a lista recarrega
    private final java.util.Set<String> mesesAbertos = new java.util.HashSet<>();
    // Meses criados em "Criar próximo mês", por morador (boletos_meses no Firestore)
    private java.util.Map<String, java.util.Set<String>> mesesCriados = new java.util.HashMap<>();

    public MoradorAdapter(Context context, List<JSONObject> moradores, OnItemClickListener listener) {
        this.context = context;
        this.moradores = moradores;
        this.listener = listener;
    }

    public void setSomenteLeitura(boolean somenteLeitura) {
        this.somenteLeitura = somenteLeitura;
    }

    public void setMesesCriados(java.util.Map<String, java.util.Set<String>> mesesCriados) {
        this.mesesCriados = mesesCriados;
        notifyDataSetChanged();
    }

    public void setBoletos(java.util.Map<String, List<Boleto>> boletos) {
        this.boletos = boletos != null ? boletos : new java.util.HashMap<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public MoradorViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_morador, parent, false);
        return new MoradorViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull MoradorViewHolder holder, int position) {
        JSONObject morador = moradores.get(position);
        String codigo = morador.optString("cod");
        String nome = morador.optString("nome");
        String cpf = morador.optString("cpf");
        String email = morador.optString("email", "");
        String telefone = morador.optString("telefone", "");
        String rua = morador.optString("rua", "");
        String numero = morador.optString("numero", "");
        String bairro = morador.optString("bairro", "");
        String cidade = morador.optString("cidade", "");
        String estado = morador.optString("estado", "");
        String quadra = morador.optString("quadra", "");
        String lote = morador.optString("lote", "");
        String imagePath = morador.optString("foto", "");

        holder.tvNomeMorador.setText(nome != null && !nome.isEmpty() ? nome : "Nome não informado");

        if (telefone != null && !telefone.isEmpty()) {
            holder.tvTelefoneMorador.setText("📞 Telefone: " + telefone);
            holder.tvTelefoneMorador.setVisibility(View.VISIBLE);
        } else {
            holder.tvTelefoneMorador.setText("📞 Telefone: Não informado");
            holder.tvTelefoneMorador.setVisibility(View.VISIBLE);
        }

        // Inquilino: abaixo do telefone, com os dados dele
        if (Inquilino.tem(morador)) {
            StringBuilder inq = new StringBuilder("👤 " + morador.optString("inquilinoNome").trim());
            String inqCpf = morador.optString("inquilinoCpf", "").trim();
            String inqTel = morador.optString("inquilinoTelefone", "").trim();
            if (!inqCpf.isEmpty()) inq.append("\n📄 CPF: ").append(inqCpf);
            if (!inqTel.isEmpty()) inq.append("\n📞 Telefone: ").append(inqTel);
            holder.tvInquilinoMorador.setText(inq.toString());
            holder.layoutInquilinoMorador.setVisibility(View.VISIBLE);
        } else {
            holder.layoutInquilinoMorador.setVisibility(View.GONE);
        }

        if (cpf != null && !cpf.isEmpty()) {
            holder.tvCpfMorador.setText("📄 CPF: " + cpf);
            holder.tvCpfMorador.setVisibility(View.VISIBLE);
        } else {
            holder.tvCpfMorador.setVisibility(View.GONE);
        }

        StringBuilder endereco = new StringBuilder();
        boolean temEndereco = false;

        if (rua != null && !rua.isEmpty()) {
            endereco.append(rua);
            temEndereco = true;
        }
        if (numero != null && !numero.isEmpty()) {
            if (temEndereco) endereco.append(", ");
            endereco.append(numero);
            temEndereco = true;
        }
        if (bairro != null && !bairro.isEmpty()) {
            if (temEndereco) endereco.append(" - ");
            endereco.append(bairro);
            temEndereco = true;
        }
        if (cidade != null && !cidade.isEmpty()) {
            if (temEndereco) endereco.append("\n📍 ");
            endereco.append(cidade);
            temEndereco = true;
        }
        if (estado != null && !estado.isEmpty()) {
            if (temEndereco && !endereco.toString().contains(cidade)) endereco.append(" - ");
            endereco.append(estado);
            temEndereco = true;
        }

        if (temEndereco) {
            holder.tvEnderecoMorador.setText("📍 Endereço: " + endereco.toString());
            holder.tvEnderecoMorador.setVisibility(View.VISIBLE);
        } else {
            holder.tvEnderecoMorador.setVisibility(View.GONE);
        }

        boolean temQuadra = (quadra != null && !quadra.isEmpty());
        boolean temLote = (lote != null && !lote.isEmpty());

        if (temQuadra || temLote) {
            if (temQuadra) {
                holder.tvQuadraMorador.setText("📐 Quadra: " + quadra);
                holder.tvQuadraMorador.setVisibility(View.VISIBLE);
            } else {
                holder.tvQuadraMorador.setVisibility(View.GONE);
            }

            if (temLote) {
                holder.tvLoteMorador.setText("🏷️ Lote: " + lote);
                holder.tvLoteMorador.setVisibility(View.VISIBLE);
            } else {
                holder.tvLoteMorador.setVisibility(View.GONE);
            }
            holder.layoutQuadraLote.setVisibility(View.VISIBLE);
        } else {
            holder.layoutQuadraLote.setVisibility(View.GONE);
        }

        if (email != null && !email.isEmpty()) {
            holder.tvEmailMorador.setText("📧 Email: " + email);
            holder.tvEmailMorador.setVisibility(View.VISIBLE);
        } else {
            holder.tvEmailMorador.setVisibility(View.GONE);
        }

        // Documentos anexados ao cadastro: abrir, baixar, exportar
        ListaAnexos.mostrar(holder.layoutAnexosItem, ListaAnexos.deValor(morador.opt("anexos")));

        carregarFoto(holder.imageMoradorItem, imagePath);

        // Lista de moradores: quadra/lote pequeno por cima da foto (em "Boletos Morador" já está no resumo)
        boolean mostrarEtiqueta = !somenteLeitura && (!quadra.trim().isEmpty() || !lote.trim().isEmpty());
        holder.tvQuadraLoteFoto.setVisibility(mostrarEtiqueta ? View.VISIBLE : View.GONE);
        if (mostrarEtiqueta) holder.tvQuadraLoteFoto.setText(etiquetaQuadraLote(quadra, lote));

        // Boletos: só na lista "Boletos Morador", que posta PDF, marca "não pago" e exclui.
        // Na lista de moradores o card fica como antes, sem boletos.
        holder.layoutSecaoBoletos.setVisibility(somenteLeitura ? View.VISIBLE : View.GONE);
        if (somenteLeitura) mostrarBoletos(holder, morador, codigo);

        holder.layoutBotoesMorador.setVisibility(somenteLeitura ? View.GONE : View.VISIBLE);

        // "Boletos Morador": card pequeno "Quadra A · Lote 01" que expande com os dados
        if (somenteLeitura) {
            holder.layoutResumoMorador.setVisibility(View.VISIBLE);
            holder.tvResumoQuadraLote.setText(tituloQuadraLote(quadra, lote));
            holder.tvResumoNome.setText(nome != null && !nome.isEmpty() ? nome : "Nome não informado");
            boolean aberto = expandidos.contains(codigo);
            holder.layoutDetalhesMorador.setVisibility(aberto ? View.VISIBLE : View.GONE);
            holder.tvResumoSeta.setText(aberto ? "▲" : "▼");
            holder.layoutResumoMorador.setOnClickListener(v -> {
                if (!expandidos.remove(codigo)) expandidos.add(codigo);
                int pos = holder.getAdapterPosition();
                if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
            });
        } else {
            holder.layoutResumoMorador.setVisibility(View.GONE);
            holder.layoutDetalhesMorador.setVisibility(View.VISIBLE);
        }

        holder.btnEditarMorador.setOnClickListener(v -> {
            if (listener != null) listener.onEditar(morador, position);
        });

        holder.btnExcluirMorador.setOnClickListener(v -> {
            if (listener != null) listener.onExcluir(morador, position);
        });
    }

    /** Meses do morador e, embaixo deles, o botão que cria o mês seguinte. */
    private void mostrarBoletos(MoradorViewHolder holder, JSONObject morador, String codigo) {
        List<Boleto> doMorador = boletos.get(codigo);
        java.util.Set<String> criados = mesesCriados.get(codigo);
        if (criados == null) {
            criados = new java.util.HashSet<>();
            mesesCriados.put(codigo, criados);
        }
        ListaBoletos.mostrarPorMes(holder.layoutBoletosMorador, codigo, doMorador, criados, new ListaBoletos.AcoesAdmin() {
            @Override
            public void marcarNaoPago(Boleto boleto, boolean naoPago) {
                if (listener != null) listener.onMarcarBoletoNaoPago(boleto, naoPago);
            }

            @Override
            public void excluir(Boleto boleto) {
                if (listener != null) listener.onExcluirBoleto(boleto);
            }

            @Override
            public void adicionar(String mes) {
                if (listener != null) listener.onAdicionarBoleto(morador, mes);
            }
        }, mesesAbertos);

        String proximo = Boleto.proximoMes(ListaBoletos.meses(doMorador, criados).last());
        java.util.Set<String> criadosDoMorador = criados;
        holder.btnCriarProximoMes.setText("Criar " + Boleto.textoDoMes(proximo));
        holder.btnCriarProximoMes.setOnClickListener(v -> {
            criadosDoMorador.add(proximo);
            mesesAbertos.add(codigo + "|" + proximo);
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION) notifyItemChanged(pos);
            if (listener != null) listener.onCriarMes(morador, proximo);
        });
    }

    private static String tituloQuadraLote(String quadra, String lote) {
        return "Quadra " + quadraTexto(quadra) + " · Lote " + loteTexto(lote);
    }

    /** Versão curta pra caber em cima da foto: "Q A · L 01". */
    private static String etiquetaQuadraLote(String quadra, String lote) {
        return "Q " + quadraTexto(quadra) + " · L " + loteTexto(lote);
    }

    private static String quadraTexto(String quadra) {
        return quadra == null || quadra.trim().isEmpty() ? "-" : quadra.trim().toUpperCase();
    }

    private static String loteTexto(String lote) {
        String l = lote == null ? "" : lote.trim();
        if (l.matches("\\d{1,9}")) l = String.format(java.util.Locale.ROOT, "%02d", Integer.parseInt(l));
        return l.isEmpty() ? "-" : l;
    }

    /** Ordem da lista do master: quadra em ordem alfabética e, dentro dela, lote em ordem numérica. */
    public static void ordenarPorQuadraELote(List<JSONObject> lista) {
        java.util.Collections.sort(lista, (a, b) -> {
            String qa = a.optString("quadra", "").trim().toUpperCase();
            String qb = b.optString("quadra", "").trim().toUpperCase();
            if (qa.isEmpty() != qb.isEmpty()) return qa.isEmpty() ? 1 : -1; // sem quadra vai pro fim
            int c = qa.compareTo(qb);
            if (c != 0) return c;
            c = Long.compare(numeroDoLote(a), numeroDoLote(b));
            if (c != 0) return c;
            c = a.optString("lote", "").compareToIgnoreCase(b.optString("lote", ""));
            return c != 0 ? c : a.optString("nome", "").compareToIgnoreCase(b.optString("nome", ""));
        });
    }

    private static long numeroDoLote(JSONObject morador) {
        String digitos = morador.optString("lote", "").replaceAll("[^0-9]", "");
        if (digitos.isEmpty() || digitos.length() > 9) return Long.MAX_VALUE;
        return Long.parseLong(digitos);
    }

    // ==================== MÉTODOS LOCAIS PARA IMAGENS (sem BackupUtil) ====================

    private void carregarFoto(ImageView imageView, String imagePath) {
        com.cjstudio.condominio_sociedade_morro_grande.data.firestore.ArquivosStorage.carregarImagem(
                imageView, imagePath, android.R.drawable.ic_menu_camera);
    }

    // ==================== FIM DOS MÉTODOS LOCAIS ====================

    @Override
    public int getItemCount() {
        return moradores.size();
    }

    public void atualizarListaCompleta(List<JSONObject> novaLista) {
        moradores.clear();
        moradores.addAll(novaLista);
        notifyDataSetChanged();
    }

    public void removerItemPorCodigo(String codigo) {
        for (int i = 0; i < moradores.size(); i++) {
            if (moradores.get(i).optString("cod").equals(codigo)) {
                moradores.remove(i);
                notifyItemRemoved(i);
                break;
            }
        }
    }

    public void atualizarItem(JSONObject moradorAtualizado, int position) {
        if (position >= 0 && position < moradores.size()) {
            moradores.set(position, moradorAtualizado);
            notifyItemChanged(position);
        }
    }

    public int encontrarPosicaoPorCodigo(String codigo) {
        for (int i = 0; i < moradores.size(); i++) {
            if (moradores.get(i).optString("cod").equals(codigo)) {
                return i;
            }
        }
        return -1;
    }

    public static class MoradorViewHolder extends RecyclerView.ViewHolder {
        ImageView imageMoradorItem;
        TextView tvNomeMorador;
        TextView tvTelefoneMorador;
        TextView tvCpfMorador;
        TextView tvEnderecoMorador;
        LinearLayout layoutQuadraLote;
        TextView tvQuadraMorador;
        TextView tvLoteMorador;
        TextView tvEmailMorador;
        LinearLayout layoutAnexosItem;
        Button btnEditarMorador;
        Button btnExcluirMorador;
        LinearLayout layoutBotoesMorador;
        LinearLayout layoutResumoMorador;
        LinearLayout layoutDetalhesMorador;
        TextView tvResumoQuadraLote;
        TextView tvResumoNome;
        TextView tvResumoSeta;
        LinearLayout layoutBoletosMorador;
        Button btnCriarProximoMes;
        LinearLayout layoutSecaoBoletos;
        TextView tvQuadraLoteFoto;
        LinearLayout layoutInquilinoMorador;
        TextView tvInquilinoMorador;

        public MoradorViewHolder(@NonNull View itemView) {
            super(itemView);
            layoutInquilinoMorador = itemView.findViewById(R.id.layoutInquilinoMorador);
            tvInquilinoMorador = itemView.findViewById(R.id.tvInquilinoMorador);
            tvQuadraLoteFoto = itemView.findViewById(R.id.tvQuadraLoteFoto);
            layoutSecaoBoletos = itemView.findViewById(R.id.layoutSecaoBoletos);
            layoutBoletosMorador = itemView.findViewById(R.id.layoutBoletosMorador);
            btnCriarProximoMes = itemView.findViewById(R.id.btnCriarProximoMes);
            layoutBotoesMorador = itemView.findViewById(R.id.layoutBotoesMorador);
            layoutResumoMorador = itemView.findViewById(R.id.layoutResumoMorador);
            layoutDetalhesMorador = itemView.findViewById(R.id.layoutDetalhesMorador);
            tvResumoQuadraLote = itemView.findViewById(R.id.tvResumoQuadraLote);
            tvResumoNome = itemView.findViewById(R.id.tvResumoNome);
            tvResumoSeta = itemView.findViewById(R.id.tvResumoSeta);
            imageMoradorItem = itemView.findViewById(R.id.imageMoradorItem);
            tvNomeMorador = itemView.findViewById(R.id.tvNomeMorador);
            tvTelefoneMorador = itemView.findViewById(R.id.tvTelefoneMorador);
            tvCpfMorador = itemView.findViewById(R.id.tvCpfMorador);
            tvEnderecoMorador = itemView.findViewById(R.id.tvEnderecoMorador);
            layoutQuadraLote = itemView.findViewById(R.id.layoutQuadraLote);
            tvQuadraMorador = itemView.findViewById(R.id.tvQuadraMorador);
            tvLoteMorador = itemView.findViewById(R.id.tvLoteMorador);
            tvEmailMorador = itemView.findViewById(R.id.tvEmailMorador);
            layoutAnexosItem = itemView.findViewById(R.id.layoutAnexosItem);
            btnEditarMorador = itemView.findViewById(R.id.btnEditarMorador);
            btnExcluirMorador = itemView.findViewById(R.id.btnExcluirMorador);
        }
    }
}
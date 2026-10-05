package it.gameshield;
import android.app.AlertDialog;
import android.content.*;
import android.os.Bundle;
import android.provider.Settings;
import android.view.WindowManager;
import android.widget.*;
import java.util.Arrays;
public final class NormalSettingsActivity extends NormalScreen {
    private TextView status;
    private NormalPinStore store;
    private boolean busy;
    private AlertDialog activeDialog;
    @Override public void onCreate(Bundle state){
        super.onCreate(state);if(BuildConfig.STRONG){finish();return;}
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        store=new NormalPinStore(this);layout("Impostazioni Normal");status=label("",20);
        label("Il PIN per disattivazione è facoltativo. Non impedisce la disinstallazione né l’accesso alle impostazioni Android. Se lo dimentichi, puoi rimuovere la VPN dalle impostazioni Android o disinstallare GameShield.",15);
        button("Imposta / modifica PIN",()->manage(false));button("Rimuovi PIN",()->manage(true));
        button("Compatibilità Android Auto",()->startActivity(new Intent(this,AndroidAutoSettingsActivity.class)));
        label("Protezione sempre attiva",21);
        label("Android permette di scegliere una VPN sempre attiva nelle impostazioni, se l’app la supporta. Questa versione Normal non la supporta: GameShield non cambia questa impostazione. Puoi aprire la pagina VPN e gestire la protezione direttamente da Android.",15);
        button("Apri impostazioni VPN Android",()->{try{startActivity(new Intent(Settings.ACTION_VPN_SETTINGS));}catch(ActivityNotFoundException e){message("Pagina VPN non disponibile su questo dispositivo");}});
        button("Torna indietro",this::finish);refresh();
    }
    private void refresh(){status.setText("PIN per disattivazione: "+(store.configured()?"attivo":"disattivato"));}
    private void manage(boolean remove){
        if(busy)return;
        String expected=store.hash();
        if(remove && expected==null){message("Il PIN è già disattivato");return;}
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText current=secret("PIN corrente"), first=secret("Nuovo PIN (6–12 cifre)"), repeat=secret("Ripeti nuovo PIN");
        if(expected!=null)box.addView(current);if(!remove){box.addView(first);box.addView(repeat);}
        TextView error=new TextView(this);box.addView(error);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(remove?"Rimuovi PIN per disattivazione":"PIN per disattivazione")
            .setView(box).setNegativeButton("Annulla",null).setPositiveButton(remove?"Rimuovi":"Salva",null).create();
        activeDialog=dialog;dialog.setOnDismissListener(d->{current.setText("");first.setText("");repeat.setText("");});
        dialog.setOnShowListener(d->{dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);dialog.getButton(-1).setOnClickListener(v->{
            if(busy)return;
            char[] old=current.getText().toString().toCharArray(), next=first.getText().toString().toCharArray(), again=repeat.getText().toString().toCharArray();
            current.setText("");first.setText("");repeat.setText("");
            if(!remove && (!NormalPinCrypto.valid(next)||!Arrays.equals(next,again))){Arrays.fill(old,'\0');Arrays.fill(next,'\0');Arrays.fill(again,'\0');error.setText("Usa da 6 a 12 cifre e ripeti lo stesso PIN.");return;}
            busy=true;dialog.getButton(-1).setEnabled(false);
            worker.execute(()->{
                String failure=null, replacement=null;
                try{
                    if(expected!=null&&!NormalPinCrypto.verify(old,expected)) failure="PIN corrente errato.";
                    else if(!remove) replacement=NormalPinCrypto.create(next);
                }catch(java.security.GeneralSecurityException e){failure="PIN non salvato. Riprova.";}
                finally{Arrays.fill(old,'\0');Arrays.fill(next,'\0');Arrays.fill(again,'\0');}
                String result=failure, encoded=replacement;
                deliver(()->{busy=false;refresh();if(!dialog.isShowing())return;dialog.getButton(-1).setEnabled(true);if(result!=null)error.setText(result);else if(!store.replace(expected,encoded))error.setText("Salvataggio non riuscito. Ripeti la verifica.");else{refresh();dialog.dismiss();message(remove?"PIN rimosso":"PIN salvato");}});
            });
        });});dialog.show();
    }
    @Override protected void onStop(){if(activeDialog!=null)activeDialog.dismiss();super.onStop();}
    private EditText secret(String hint){EditText e=new EditText(this);e.setHint(hint);e.setSingleLine(true);e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER|android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(12)});e.setSaveEnabled(false);e.setImportantForAutofill(android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI|android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);return e;}
}

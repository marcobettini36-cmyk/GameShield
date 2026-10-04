package it.gameshield;
import android.app.*;
import android.os.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;
abstract class NormalScreen extends Activity {
    final Handler handler=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    LinearLayout root;
    void layout(String title) {
        getWindow().setStatusBarColor(0xff072d36); getWindow().setNavigationBarColor(0xff072d36);
        ScrollView scroll=new ScrollView(this); root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,32,24,32); root.setBackgroundColor(Color.rgb(241,247,247)); scroll.addView(root); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,i)->{root.setPadding(dp(22),dp(28)+i.getSystemWindowInsetTop(),dp(22),dp(32)+i.getSystemWindowInsetBottom());return i;});
        label("GAMESHIELD / NORMAL",14); label(title,25);
    }
    TextView label(String text,int size) { TextView v=new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(0xff072d36); v.setPadding(0,dp(8),0,dp(8)); if(size>=20)v.setTypeface(null,Typeface.BOLD); root.addView(v); return v; }
    Button button(String text,Runnable action) { Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setTextColor(0xff087f8c); b.setOnClickListener(v->action.run());root.addView(b,new LinearLayout.LayoutParams(-1,-2));return b; }
    EditText field(String hint,boolean secret) {
        EditText e=new EditText(this); e.setHint(hint); e.setSingleLine(true);
        e.setInputType(InputType.TYPE_CLASS_NUMBER|(secret?InputType.TYPE_NUMBER_VARIATION_PASSWORD:0));
        e.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(secret?12:6)});
        e.setSaveEnabled(false); e.setImportantForAutofill(View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS);
        e.setImeOptions(android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI|android.view.inputmethod.EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING);
        root.addView(e);return e;
    }
    void message(String text) { Toast.makeText(this,text,Toast.LENGTH_LONG).show(); }
    void deliver(Runnable action) { handler.post(()->{if(!isFinishing()&&!isDestroyed())action.run();}); }
    int dp(int n) {return Math.round(n*getResources().getDisplayMetrics().density);}
    @Override protected void onDestroy() {handler.removeCallbacksAndMessages(null);worker.shutdownNow();super.onDestroy();}
}

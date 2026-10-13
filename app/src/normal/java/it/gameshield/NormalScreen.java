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
        getWindow().setStatusBarColor(0xff101816); getWindow().setNavigationBarColor(0xff101816);
        ScrollView scroll=new ScrollView(this); root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,32,24,32); root.setBackgroundColor(0xff101816); scroll.setFillViewport(true); scroll.addView(root); setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,i)->{root.setPadding(dp(22),dp(28)+i.getSystemWindowInsetTop(),dp(22),dp(32)+i.getSystemWindowInsetBottom());return i;});
        TextView brand=label("GAMESHIELD / NORMAL",14);brand.setTextColor(0xff58e5aa);brand.setLetterSpacing(.12f);label(title,25);
    }
    TextView panel(String text,int size){TextView v=label(text,size);android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable();bg.setColor(0xff1b2822);bg.setCornerRadius(dp(20));bg.setStroke(dp(1),0xff345243);v.setBackground(bg);v.setPadding(dp(18),dp(18),dp(18),dp(18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(10),0,dp(10));v.setLayoutParams(p);return v;}
    TextView label(String text,int size) { TextView v=new TextView(this); v.setText(text); v.setTextSize(size); v.setTextColor(size>=20?0xfff1fff8:0xffc9ded3); v.setPadding(0,dp(8),0,dp(8)); if(size>=20)v.setTypeface(null,Typeface.BOLD); root.addView(v); return v; }
    Button button(String text,Runnable action) { Button b=new Button(this); b.setText(text); b.setAllCaps(false); b.setTextColor(0xffeffff6); b.setMinHeight(dp(56)); b.setTextSize(16); android.graphics.drawable.GradientDrawable bg=new android.graphics.drawable.GradientDrawable(); bg.setColor(0xff145c44); bg.setCornerRadius(dp(16)); b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x4458e5aa),bg,null)); b.setPadding(dp(16),dp(12),dp(16),dp(12)); b.setOnClickListener(v->action.run());LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.setMargins(0,dp(5),0,dp(5));root.addView(b,p);return b; }
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

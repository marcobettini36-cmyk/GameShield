package it.gameshield.fixture;
public final class FixtureActivity extends android.app.Activity {
    @Override public void onCreate(android.os.Bundle b){super.onCreate(b);android.widget.TextView v=new android.widget.TextView(this);v.setText("GameShield synthetic gambling-app fixture. No gambling, network or payments.");v.setTextSize(24);setContentView(v);}
}

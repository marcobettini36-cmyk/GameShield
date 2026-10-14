package it.gameshield;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Original vector diagrams for the guide; not screenshots of NextDNS. */
final class NormalDnsDiagram extends View {
    private final int step;
    NormalDnsDiagram(Context context,int step){super(context);this.step=step;setContentDescription(new String[]{"Account esterno e configurazione","Filtro gioco attivo, altri filtri non necessari","Copia il nome host completo","Incolla e salva sul telefono","Inserisci il nome host nelle impostazioni Android","Verifica siti innocui e campione DNS"}[step]);}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);canvas.save();float scale=Math.min(getWidth()/280f,getHeight()/100f);canvas.translate((getWidth()-280*scale)/2,(getHeight()-100*scale)/2);canvas.scale(scale,scale);Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);p.setColor(0xff58e5aa);p.setStrokeWidth(3);p.setStyle(Paint.Style.STROKE);
        canvas.drawRoundRect(12,12,92,88,12,12,p);canvas.drawRoundRect(188,12,268,88,12,12,p);canvas.drawLine(108,50,170,50,p);canvas.drawLine(158,39,170,50,p);canvas.drawLine(158,61,170,50,p);
        if(step==0){canvas.drawCircle(52,36,10,p);canvas.drawArc(32,48,72,78,180,180,false,p);}
        else if(step==1){canvas.drawCircle(52,50,21,p);canvas.drawLine(36,34,68,66,p);}
        else {canvas.drawLine(28,32,76,32,p);canvas.drawLine(28,49,68,49,p);canvas.drawLine(28,66,60,66,p);}
        if(step==4){canvas.drawRoundRect(210,21,246,79,5,5,p);canvas.drawLine(220,69,236,69,p);}
        else {canvas.drawLine(206,51,221,66,p);canvas.drawLine(221,66,250,35,p);}
        canvas.restore();
    }
}

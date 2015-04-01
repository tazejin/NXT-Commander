package com.jurajpaulik.nxtcommander;

import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

// Trieda sluzi na vyhladavanie suborov v kocke a ich nasledne
// zobrazenie ako dialog po kliknuti na "Action Bar"
class Subory {

    private Activity myActivity;
    private CharSequence[] programs;
    
    public Subory(Activity activity, List<String> list) {
        myActivity = activity;
        // kopirovanie stringu z listu do Charsequence pola
        programs = new CharSequence[list.size()];
        Iterator<String> iterator = list.iterator();
        int position = 0;
        while(iterator.hasNext()) {
            programs[position++] = iterator.next();
        } 	        
    }    

    // Ukazanie suborov, ktore sme nasli ako dialog v aktivity
	public void show() {
        AlertDialog.Builder builder = new AlertDialog.Builder(myActivity);
        builder.setItems(programs, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int item) {
                startProgram(item);
            }
        });
        builder.create().show();        
	}
	
	private void startProgram(int number) {
        ((Main) myActivity).startProgram((String) programs[number]);
	}
		    
}

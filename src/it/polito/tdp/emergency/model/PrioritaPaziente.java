package it.polito.tdp.emergency.model;

import java.util.Comparator;

import it.polito.tdp.emergency.model.Paziente.StatoPaziente;

public class PrioritaPaziente implements Comparator<Paziente> {

	@Override
	public int compare(Paziente p1, Paziente p2) {
		// se p1 ha precedenza su p2 restituisco un valore negativo
		// viceversa restituisco un valore positivo
		
		// per verifica su stato rosso (rosso contro non rosso e viceversa)
		if(p1.getStato()==StatoPaziente.WAITING_RED && p2.getStato()!=StatoPaziente.WAITING_RED)
			return -1;
		if(p1.getStato()!=StatoPaziente.WAITING_RED && p2.getStato()==StatoPaziente.WAITING_RED)
			return +1;
		
		// per verifica su stato giallo
		if(p1.getStato()==StatoPaziente.WAITING_YELLOW && p2.getStato()!=StatoPaziente.WAITING_YELLOW)
				return -1;
		if(p1.getStato()!=StatoPaziente.WAITING_YELLOW && p2.getStato()==StatoPaziente.WAITING_YELLOW)
				return +1;
		
		// bianco con non bianco già verificato come conseguenza dei casi precedenti
		
		// per verifica su rosso con rosso, giallo con giallo e bianco con bianco
		// e quindi l'ora di arrivo
		return p1.getOraArrivo().compareTo(p2.getOraArrivo());
	}

}

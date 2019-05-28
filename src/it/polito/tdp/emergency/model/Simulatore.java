package it.polito.tdp.emergency.model;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;

import it.polito.tdp.emergency.model.Evento.TipoEvento;
import it.polito.tdp.emergency.model.Paziente.StatoPaziente;

public class Simulatore {

	// Coda degli eventi
	private PriorityQueue<Evento> queue = new PriorityQueue<>();

	// Modello del Mondo
	private List<Paziente> pazienti;
	private PriorityQueue<Paziente> salaAttesa;
	private int studiLiberi;

	// Parametri di simulazione
	private int NS = 3; // numero di studi medici
	private int NP = 50; // numero di pazienti in arrivo
	private Duration T_ARRIVAL = Duration.ofMinutes(15); // intervallo di tempo tra i pazienti

	private LocalTime T_inizio = LocalTime.of(8, 0);
	private LocalTime T_fine = LocalTime.of(20, 0);

	private int DURATION_TRIAGE = 5;
	private int DURATION_WHITE = 10;
	private int DURATION_YELLOW = 15;
	private int DURATION_RED = 30;
	private int TIMEOUT_WHITE = 120;
	private int TIMEOUT_YELLOW = 60;
	private int TIMEOUT_RED = 90;

	// Statistiche da calcolare
	private int numDimessi;
	private int numAbbandoni;
	private int numMorti;
	
	// Variabili interne
	private StatoPaziente nuovoStatoPaziente;
	private Duration intervalloPolling = Duration.ofMinutes(5);
	
	public Simulatore() {
		this.pazienti = new ArrayList<>();
	}
		
	public void init() {
		// Creare pazienti
		LocalTime oraArrivo = this.T_inizio;
		pazienti.clear();
		for(int i=0; i<NP;i++) {
			Paziente p = new Paziente(i+1, oraArrivo);
			pazienti.add(p);
			
			oraArrivo = oraArrivo.plus(T_ARRIVAL); // ora arrivo per il paziente successivo
		}
		
		// Inizializzo la sala d'attesa vuota
		this.salaAttesa = new PriorityQueue<>(new PrioritaPaziente());
		
		// Creare gli studi medici
		this.studiLiberi=this.NS;
		
		nuovoStatoPaziente = nuovoStatoPaziente.WAITING_WHITE;
		
		// Creare eventi iniziali (di arrivo dei pazienti)
		queue.clear();
		for(Paziente p : pazienti) {
			queue.add(new Evento(p.getOraArrivo(), TipoEvento.ARRIVO, p));
		}
		// lancia l'osservatore in polling
		queue.add(new Evento(this.T_inizio.plus(intervalloPolling), TipoEvento.POLLING, null));
		
		
		// Resettare le statistiche
		numDimessi = 0;
		numAbbandoni =0;
		numMorti=0;
	}
	
	public void run() {
		while(!queue.isEmpty()) {
			Evento ev = queue.poll();
//			System.out.println(ev);
			Paziente p = ev.getPaziente();
			
			/* se la simulazione dovesse terminare alle 20.00
			if(ev.getOra().isAfter(T_fine)) {
				break;
			}
			*/
			
			switch(ev.getTipo()) {
			
			case ARRIVO:
				// tra 5 minuti verrà assegnato un codice colore
				queue.add(new Evento(ev.getOra().plusMinutes(DURATION_TRIAGE), 
						TipoEvento.TRIAGE, ev.getPaziente()));
				break;
				
			case TRIAGE:
				// assegno un codice colore (asegnazione random a rotazione)
				p.setStato(nuovoStatoPaziente);
				
				if(p.getStato()==StatoPaziente.WAITING_WHITE)
					queue.add(new Evento(ev.getOra().plusMinutes(TIMEOUT_WHITE), TipoEvento.TIMEOUT, p));
				else if(p.getStato()==StatoPaziente.WAITING_YELLOW)
					queue.add(new Evento(ev.getOra().plusMinutes(TIMEOUT_YELLOW), TipoEvento.TIMEOUT, p));
				else if(p.getStato()==StatoPaziente.WAITING_RED)
					queue.add(new Evento(ev.getOra().plusMinutes(TIMEOUT_RED), TipoEvento.TIMEOUT, p));
				
				salaAttesa.add(p);
				
				ruotaNuovoStatoPaziente();
				break;
			
			case VISITA:
				// determina il paziente con max priorità
				Paziente pazChiamato = salaAttesa.poll();
				if(pazChiamato==null)
					break;
				// paziente enttra in uno studio
				StatoPaziente vecchioStato = pazChiamato.getStato();
				pazChiamato.setStato(StatoPaziente.TREATING);
				
				// studio diventa occupato
				studiLiberi--;
				
				// schedula l'uscita (CURATO) del paziente
				if(vecchioStato == StatoPaziente.WAITING_RED) {
					queue.add(new Evento(ev.getOra().plusMinutes(DURATION_RED), 
							TipoEvento.CURATO, pazChiamato));
				} else if(vecchioStato == StatoPaziente.WAITING_YELLOW) {
					queue.add(new Evento(ev.getOra().plusMinutes(DURATION_YELLOW), 
							TipoEvento.CURATO, pazChiamato));
				} else if(vecchioStato == StatoPaziente.WAITING_WHITE) {
					queue.add(new Evento(ev.getOra().plusMinutes(DURATION_WHITE), 
							TipoEvento.CURATO, pazChiamato));
				}
				break;
			
			case CURATO:
				// paziente è fuori
				p.setStato(StatoPaziente.OUT);
				
				// aggiorna numDimessi
				numDimessi--;
				
				// schedula evento VISITA "adesso dato che lo studio ora è libero nel caso in cui sono sempre pieni
				studiLiberi++;
				queue.add(new Evento(ev.getOra(), TipoEvento.VISITA, null));
				
				// serve altro meccanismo per alimentare quando gli studi sono vuoti ogni tot tempo
				// oppure si può agire su triage
				break;
				
			case TIMEOUT:
				// per aggiornare la priorità nel caso di cambio waiting da gaillo a rosso
				// rimuovi dalla lista d'attesa e dopo riaggiungo con nuovo stato
				salaAttesa.remove(p);
				
				Paziente paz = ev.getPaziente();
				if(p.getStato()==StatoPaziente.WAITING_WHITE) {
					p.setStato(StatoPaziente.OUT);
					this.numAbbandoni++;
				} else if(p.getStato()==StatoPaziente.WAITING_YELLOW) {
					p.setStato(StatoPaziente.WAITING_RED);
					queue.add(new Evento(ev.getOra().plusMinutes(TIMEOUT_RED), TipoEvento.TIMEOUT, p));	
					salaAttesa.add(p);
				} else if(p.getStato()==StatoPaziente.WAITING_RED) {
					p.setStato(StatoPaziente.BLACK);
					this.numMorti++;
				} else {
					System.out.println("Timeout anomalo nello stato "+p.getStato()+
							"\n Può essere dovuto a stato di TREATING anzichè WAITING");
				}
				
				break;
			case POLLING:
				// verfiica se ci sono pazienti in attesa con studi liberi
				if(!salaAttesa.isEmpty() && studiLiberi>0) {
					queue.add(new Evento(ev.getOra(), TipoEvento.VISITA, null));
				}
				// richedula se stesso
				if(ev.getOra().isBefore(T_fine)) {
					queue.add(new Evento(ev.getOra().plus(intervalloPolling), 
							TipoEvento.POLLING, null));
				}
						
				break;
		}
		
	}
}

	private void ruotaNuovoStatoPaziente() {
		if(nuovoStatoPaziente == StatoPaziente.WAITING_WHITE)
			nuovoStatoPaziente = StatoPaziente.WAITING_YELLOW; 
		else if(nuovoStatoPaziente == StatoPaziente.WAITING_YELLOW)
			nuovoStatoPaziente = StatoPaziente.WAITING_RED; 
		else if(nuovoStatoPaziente == StatoPaziente.WAITING_RED)
			nuovoStatoPaziente = StatoPaziente.WAITING_WHITE; 
		
	}

	public PriorityQueue<Evento> getQueue() {
		return queue;
	}

	public void setQueue(PriorityQueue<Evento> queue) {
		this.queue = queue;
	}

	public List<Paziente> getPazienti() {
		return pazienti;
	}

	public void setPazienti(List<Paziente> pazienti) {
		this.pazienti = pazienti;
	}

	public int getStudiLiberi() {
		return studiLiberi;
	}

	public void setStudiLiberi(int studiLiberi) {
		this.studiLiberi = studiLiberi;
	}

	public int getNS() {
		return NS;
	}

	public void setNS(int nS) {
		NS = nS;
	}

	public int getNP() {
		return NP;
	}

	public void setNP(int nP) {
		NP = nP;
	}

	public Duration getT_ARRIVAL() {
		return T_ARRIVAL;
	}

	public void setT_ARRIVAL(Duration t_ARRIVAL) {
		T_ARRIVAL = t_ARRIVAL;
	}

	public LocalTime getT_inizio() {
		return T_inizio;
	}

	public void setT_inizio(LocalTime t_inizio) {
		T_inizio = t_inizio;
	}

	public LocalTime getT_fine() {
		return T_fine;
	}

	public void setT_fine(LocalTime t_fine) {
		T_fine = t_fine;
	}

	public int getDURATION_TRIAGE() {
		return DURATION_TRIAGE;
	}

	public void setDURATION_TRIAGE(int dURATION_TRIAGE) {
		DURATION_TRIAGE = dURATION_TRIAGE;
	}

	public int getDURATION_WHITE() {
		return DURATION_WHITE;
	}

	public void setDURATION_WHITE(int dURATION_WHITE) {
		DURATION_WHITE = dURATION_WHITE;
	}

	public int getDURATION_YELLOW() {
		return DURATION_YELLOW;
	}

	public void setDURATION_YELLOW(int dURATION_YELLOW) {
		DURATION_YELLOW = dURATION_YELLOW;
	}

	public int getDURATION_RED() {
		return DURATION_RED;
	}

	public void setDURATION_RED(int dURATION_RED) {
		DURATION_RED = dURATION_RED;
	}

	public int getTIMEOUT_WHITE() {
		return TIMEOUT_WHITE;
	}

	public void setTIMEOUT_WHITE(int tIMEOUT_WHITE) {
		TIMEOUT_WHITE = tIMEOUT_WHITE;
	}

	public int getTIMEOUT_YELLOW() {
		return TIMEOUT_YELLOW;
	}

	public void setTIMEOUT_YELLOW(int tIMEOUT_YELLOW) {
		TIMEOUT_YELLOW = tIMEOUT_YELLOW;
	}

	public int getTIMEOUT_RED() {
		return TIMEOUT_RED;
	}

	public void setTIMEOUT_RED(int tIMEOUT_RED) {
		TIMEOUT_RED = tIMEOUT_RED;
	}

	public int getNumDimessi() {
		return numDimessi;
	}

	public void setNumDimessi(int numDimessi) {
		this.numDimessi = numDimessi;
	}

	public int getNumAbbandoni() {
		return numAbbandoni;
	}

	public void setNumAbbandoni(int numAbbandoni) {
		this.numAbbandoni = numAbbandoni;
	}

	public int getNumMorti() {
		return numMorti;
	}

	public void setNumMorti(int numMorti) {
		this.numMorti = numMorti;
	}

	public StatoPaziente getNuovoStatoPaziente() {
		return nuovoStatoPaziente;
	}

	public void setNuovoStatoPaziente(StatoPaziente nuovoStatoPaziente) {
		this.nuovoStatoPaziente = nuovoStatoPaziente;
	}
	
	
	
	
}

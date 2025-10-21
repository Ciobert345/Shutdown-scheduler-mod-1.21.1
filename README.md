# 🕒 Shutdown Scheduler (Fabric Mod)

Una semplice mod Fabric per **Minecraft 1.21.1** che permette di **pianificare lo spegnimento automatico del server** tramite comando o logica interna.  
Perfetta per server privati o dedicati che devono arrestarsi automaticamente a un'ora specifica.

---

## 🚀 Funzionalità

- Permette di **spegnere automaticamente il server** dopo un certo orario o periodo.  
- Supporta **esecuzione di comandi** prima dello shutdown.  
- Log chiari in console per monitorare il processo.  
- Compatibile con **Fabric Loader 0.17.2+** e **Minecraft 1.21.1**.

---

## ⚙️ Installazione

### 🧩 Requisiti
- **Minecraft 1.21.1**
- **Fabric Loader 0.17.2 o superiore**
- **Fabric API** installata nel server

🛠️ Comandi disponibili
Comando	Descrizione
/shutdownscheduler add <giorno> <ora> <minuti>	Aggiunge un orario di spegnimento programmato.
Esempio: /shutdownscheduler add lunedi 23 30
/shutdownscheduler remove <giorno> <ora> <minuti>	Rimuove un orario di spegnimento precedentemente aggiunto.
Esempio: /shutdownscheduler remove venerdi 18 00
/shutdownscheduler list	Mostra tutti gli orari di spegnimento configurati.
/shutdownscheduler reload	Ricarica il file di configurazione shutdown_scheduler.json senza riavviare il server.
/shutdownscheduler test	Esegue un test immediato, mostrando in chat il prossimo spegnimento programmato.
/shutdownscheduler force	Forza lo spegnimento immediato del server (equivalente a /stop).

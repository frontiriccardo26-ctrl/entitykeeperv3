# EntityKeeper — Forge 1.20.1 Mod

Mantiene le entità caricate oltre la render distance e permette di impostare
un timer di despawn forzato, anche su entità moddificate che normalmente non scompaiono.

---

## Comandi

Tutti i comandi richiedono **permission level 2** (operatore).

### Impostare una regola
```
/entitykeeper set <modid:tipo_entità> <forceload> <despawn_secondi>
```

| Parametro         | Tipo    | Descrizione |
|-------------------|---------|-------------|
| `modid:tipo`      | string  | Resource location dell'entità (es. `minecraft:arrow`, `mymod:my_arrow`) |
| `forceload`       | boolean | `true` = mantiene caricato il chunk in cui si trova l'entità |
| `despawn_secondi` | integer | Secondi prima del despawn forzato. `-1` = nessun despawn forzato |

**Esempi:**
```
# Freccia vanilla: chunk caricato, despawn dopo 60 secondi
/entitykeeper set minecraft:arrow true 60

# Freccia moddata: solo despawn forzato dopo 2 minuti, nessun forceload
/entitykeeper set mymod:my_arrow false 120

# Entità sempre caricata, mai despawn forzato
/entitykeeper set mymod:my_entity true -1
```

### Rimuovere una regola
```
/entitykeeper remove <modid:tipo_entità>
```

### Vedere tutte le regole attive
```
/entitykeeper list
```

---

## Come funziona

**ForceLoad:**
Ogni tick, per ogni entità con una regola `forceLoad=true`, la mod chiama
`level.setChunkForced(chunkX, chunkZ, true)` sul chunk dove si trova l'entità.
Quando l'entità si sposta in un chunk diverso, il ticket sul vecchio chunk viene rimosso
e aggiunto su quello nuovo. Quando l'entità viene rimossa (o perde il tracking),
il ticket forceload viene liberato automaticamente.

**Despawn timer:**
Un contatore interno (in ticks) parte da quando l'entità viene vista per la prima volta.
Quando supera `despawnSeconds × 20`, l'entità viene eliminata con `entity.discard()`.
Questo funziona anche su entità che normalmente non despawnano affatto
(es. frecce moddificate, proiettili custom).

**Persistenza:**
Le regole sono salvate in `<world>/data/entitykeeper.dat` tramite il sistema
`SavedData` di Forge. Sopravvivono ai riavvii del server.

---

## Build

### Prerequisiti
- JDK 17
- Connessione internet (per scaricare Forge e le dipendenze Gradle)

### Compilare
```bash
cd entitykeeper
./gradlew build
```

Il file `.jar` si troverà in:
```
build/libs/entitykeeper-1.0.0.jar
```

### Installare
Copia il `.jar` nella cartella `mods/` del tuo server o client Forge 1.20.1.

---

## Note tecniche

- I tick counter sono in memoria: al riavvio il timer riparte da zero per ogni entità.
  Solo le **regole** vengono salvate su disco, non lo stato runtime delle singole entità.
- Il forceload usa ticket di tipo `FORCED` standard di Minecraft, compatibile con
  il normale sistema di chunk loading.
- La mod funziona **solo lato server** per il forceload e il despawn.
  Non è necessaria lato client.

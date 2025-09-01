---
applyTo: '**'
---

# Dokumentace pro vývojáře – Plugin  
# „Týdenní regionální volby“

**Verze specifikace:** 24. 7. 2025  

Tento dokument v češtině popisuje funkční a technické požadavky na implementaci pluginu,  
který zavádí týdenní volby s reputačním systémem, regionálními rolemi, plně automatickými  
NPC a grafickým rozhraním. Dokument je čistě textový, neobsahuje žádné ukázky zdrojového kódu.  
Pro bližší informace mě kontaktujte v DM.  

---

## POPIS

Každý týden se volby konají pouze v jednom regionu; regiony se střídají v pořadí.  

Hráči v daném regionu mohou:  

- přihlásit se do voleb  
- hlasovat  
- sledovat výsledky  

Vítězové získávají role s pravomocemi platnými pouze v daném regionu.  

Role, reputace a všechny bonusy jsou spravovány přes **LuckPerms**.  
Veškerá interakce probíhá pomocí **GUI** a **NPC**.  

---

## Pevně dané regiony

| ID        | Název     |
|-----------|-----------|
| vojtechov | Vojtěchov |
| tresin    | Třešín    |
| pribyslav | Přibyslav |
| drahosov  | Drahošov  |

Regiony se střídají v tomto pořadí a každý z nich má vlastní hranice pomocí **RavarynRegion**  
(Potřeba implementovat do pluginu), NPC a hologram.  

---

## Role a jejich regionální pravomoci

| Role         | Rozsah (pouze v daném regionu)                               | Odměna reputace |
|--------------|---------------------------------------------------------------|-----------------|
| Zalařník     | Správa trestů, může hráče dát do žalaře za porušení           | +10 pro vítěze  |
| Rychtář      | Lokální „zákony“ a globální regionální bonusy                 | +10 pro vítěze  |
| Správce obchodu | Dynamická úprava cen v regionálním obchodě                 | +10 pro vítěze  |

- Neúspěšní kandidáti získají **+2 reputace**  
- Každý platný volič získá **+1 reputaci**  

Pravomoci jsou implementovány přes **LuckPerms** – každá role odpovídá LP skupině nebo `region=<id>`.  

---

## Dvoutýdenní volební cyklus

| Týden | Fáze            | Typické akce |
|-------|-----------------|--------------|
| 1     | Přihlášky       | NPC přijímá kandidáty a ověřuje podmínky účasti. |
| 1     | Hlasování       | Po uzavření přihlášek se automaticky otevře hlasovací GUI. |
| 2     | Výkon mandátu   | Vyhlásí se vítěz, přiřadí role, přidělí reputaci a aktivuje efekty. |
| 2     | Rotace regionu  | Na konci týdne se cyklus přesune do dalšího regionu a znovu se otevřou přihlášky. |

---

## Podmínky účasti

Hráč se může ucházet o funkci pouze tehdy, pokud v době podání přihlášky splňuje:  

- minimálně 20 hodin čistého herního času  
- minimálně 1 quest bod  
- minimálně 1 000 mincí  

Hodnoty jsou nastavitelné v `elections_requirement.yml`.  

---

## Vymezení hranic regionu

- vše funguje přes náš plugin  

---

## Vytváření NPC a hologramů

### 7.1 Ruční příkaz

`/volby <region>` (přednastavené 4)  

Po zadání:  

- NPC se automaticky vytvoří v místě, kde stojíš, s parametry:  
  - jméno: `&a&lVolby`  
  - druhý holografický řádek: `&7Oblast: &e<Název>`  
- NPC získá interní metadata `region_id=<id>` a `role=election_commissioner`.  

### Vlastnosti NPC

- Bez gravitace, chráněný proti poškození a pohybovým efektům.  
- Kliknutí pravým tlačítkem otevře dynamické GUI podle fáze voleb.  
- Plugin kontroluje existenci NPC při startu serveru; chybějící NPC se automaticky znovu vytvoří.  

---

## Grafická rozhraní

**Hlavní menu:**
- Přihlásit se do voleb  
- Zobrazit kandidáty  
- Hlasovat  
- Výsledky  

**Formulář kandidatury:**
- Výběr role (4 tlačítka)  
- Krátký slogan (max. 100 znaků)  
- Potvrdit / zrušit  

**Hlasovací menu:**
- Pětistránkový seznam (automatické stránkování)  
- Zvýraznění kandidáta, pro kterého hráč již hlasoval  
- Zabránění opakovanému hlasování  

**Historie:**
- Jména vítězů + procento hlasů za poslední 3 kola  

GUI používá preferovanou serverovou knihovnu pro inventářové menu; všechny texty, názvy a zprávy jsou lokalizovatelné v `gui.yml`.  

---

## Integrace s LuckPerms

**Přiřazení pravomocí vítězům:**
- Po vyhlášení výsledků plugin přidá hráči LuckPerms roli/skupinu s kontextem `region=<id>` na 30 dní.  

**Kontrola využití schopností:**
- Při každém pokusu o akci vázanou na roli plugin ověří aktuální souřadnice hráče oproti uloženému kvádru. Pokud není uvnitř, akce se tiše odmítne.  

**Odebrání pravomocí:**
- Plugin automaticky odstraní uzly regionálních vítězů po 30 dnech.  

**Reputace a prefixy:**
- Přidělení reputace je součástí stejného plánovaného úkolu.  
- Při dosažení nastavených milníků se automaticky aplikuje dekorativní prefix definovaný v `reputation_rewards.prefix_levels`.  

---

## Administrační příkazy (přehled)

| Příkaz              | Funkce |
|---------------------|--------|
| `/volby <id>`       | Vytvoří volebního komisaře NPC na pozici hráče. |
| `/volby reload`     | Načte znovu všechny konfigurační soubory pluginu. |
| `/volby rotate`     | Ručně ukončí aktuální volby a přesune cyklus do dalšího regionu. |
| `/volby reputation <p> <±n>` | Přidá nebo odebere hráči reputaci. |
| `/volby fixnpcs`    | Zkontroluje existenci všech čtyř komisařů a v případě potřeby je obnoví. |

**Oprávnění:**
- hráč – `elections.use`  
- moderátor – `elections.mod`  
- administrátor – `elections.admin`  

---

## Úložiště dat

- **Konfigurace YAML** – statická data o regionech, textech GUI, odměnách  
- **SQL tabulky** – kandidáti, hlasy, držitelé rolí, reputace  
- **Data Citizens** – identifikátory NPC (spravuje Citizens, plugin pouze čte ID)  

Silné požadavky na transakce platí pouze pro hlasování a vyhlašování výsledků – tyto operace musí být atomické.  

---

## Závěr

Tato specifikace poskytuje vývojáři kompletní slovní popis všech funkcí:  

- automatický NPC s hologramem  
- plně regionální role přes LuckPerms  
- dvoutýdenní volební proces  
- kompletní GUI a admin příkazy  

Dodržení zde uvedených pravidel vytvoří robustní a rozšiřitelný systém, který motivuje hráče k dlouhodobé aktivitě a zároveň udržuje jasné řízení pravomocí v jednotlivých oblastech.  

## API dokumentace

LuckPerms API poskytuje rozhraní pro interakci s pluginem LuckPerms. Umožňuje správu uživatelských oprávnění a rolí v rámci herního serveru.

```
<dependencies>
    <dependency>
        <groupId>net.luckperms</groupId>
        <artifactId>api</artifactId>
        <version>5.4</version>
        <scope>provided</scope>
    </dependency>
</dependencies>```

```
repositories {
    mavenCentral()
}

dependencies {
    compileOnly 'net.luckperms:api:5.4'
}```

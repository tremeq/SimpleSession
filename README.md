# SimpleSession

Modern session time tracking plugin for Minecraft 1.21

## Description / Opis

**[English]**

SimpleSession is a modern alternative to SessionTime, designed to track player session duration on Minecraft servers. Besides the current session it keeps persistent statistics (SQLite), protects sessions against short relogs and provides many PlaceholderAPI placeholders for scoreboards, tab lists, holograms and GUIs.

**[Polski]**

SimpleSession to nowoczesna alternatywa dla SessionTime, śledząca czas sesji graczy. Oprócz bieżącej sesji przechowuje trwałe statystyki (SQLite), chroni sesję przed krótkim relogiem i udostępnia wiele placeholderów PlaceholderAPI do scoreboardów, tabu, hologramów i GUI.

---

## Features / Funkcje

**[English]**
- Real-time session tracking (session starts before other plugins' join handlers run)
- Persistent statistics in SQLite: total play time, longest session, number of sessions, today / this week / this month, average session, first join, last seen
- Leaderboards: current sessions, total, record, today, week, month, sessions - with pages
- Relog protection: a player returning within the grace period continues the old session (configurable time, optional counting of offline time, optional survival of a restart)
- Sessions survive `/reload`
- Smart time formats: only meaningful units with correct Polish/English plural forms ("1 godzina i 5 minut") - can be disabled globally or per format
- Named time formats + `display` section: choose which format is used in every place (leaderboard, placeholders, /ss check, milestones...)
- Admin commands: `/ss check`, `/ss reset`, `/ss set|add|take`
- Session milestones with message, broadcast, permission and actions (console/player commands, title, action bar, sound)
- Fully customizable messages (`messages.yml`, hex colors `&#RRGGBB`)
- Automatic one-time update of old configuration files (with backup)
- Thread-safe placeholders, database on its own thread, cached leaderboards

**[Polski]**
- Śledzenie sesji w czasie rzeczywistym (sesja startuje zanim inne pluginy obsłużą wejście gracza)
- Trwałe statystyki w SQLite: łączny czas gry, najdłuższa sesja, liczba sesji, dzisiaj / tydzień / miesiąc, średnia sesja, pierwsze wejście, ostatnio widziany
- Rankingi: bieżące sesje, łączny czas, rekord, dzisiaj, tydzień, miesiąc, liczba sesji - ze stronami
- Ochrona przed relogiem: gracz który wróci w wyznaczonym czasie kontynuuje sesję (konfigurowalny czas, opcjonalne liczenie czasu offline, opcjonalnie także po restarcie)
- Sesje przetrwają `/reload`
- Inteligentne formaty czasu: tylko potrzebne jednostki z poprawną odmianą ("1 godzina i 5 minut") - można wyłączyć globalnie lub dla formatu
- Nazwane formaty czasu + sekcja `display`: wybierasz format dla każdego miejsca (topka, placeholdery, /ss check, kamienie milowe...)
- Komendy administracyjne: `/ss check`, `/ss reset`, `/ss set|add|take`
- Kamienie milowe z wiadomością, ogłoszeniem, uprawnieniem i akcjami (komendy, title, action bar, dźwięk)
- W pełni konfigurowalne wiadomości (`messages.yml`, kolory hex `&#RRGGBB`)
- Automatyczna, jednorazowa aktualizacja starych plików konfiguracyjnych (z kopią zapasową)
- Placeholdery bezpieczne wątkowo, baza danych na osobnym wątku, cache rankingów

---

## Requirements / Wymagania

- Minecraft Server 1.21+ (Spigot / Paper / Purpur)
- Java 21
- PlaceholderAPI (optional but recommended / opcjonalnie, zalecane)
- SQLite driver is bundled with Spigot/Paper - nothing to install / sterownik SQLite jest wbudowany w Spigot/Paper

---

## Installation / Instalacja

1. Place the JAR in `plugins` / Umieść JAR w `plugins`
2. Install PlaceholderAPI / Zainstaluj PlaceholderAPI
3. Restart the server / Zrestartuj serwer
4. Configure `plugins/SimpleSession/config.yml` and `messages.yml`

### Updating from 1.0.0 to 2.0.0 / Aktualizacja z 1.0.0 do 2.0.0

**[English]**
- On the first start the plugin backs up `config.yml` and `messages.yml` (`config-backup-v1.yml`, `messages-backup-v1.yml`) and adds every new option with its comments. This happens once (`config-version: 2`).
- Your values are kept. The old `time-formats.full` stays a plain pattern - replace it with the new smart section from the bundled config if you want smart formatting.
- Your milestones list is not changed. Old `broadcast ...` commands still work on servers without a `/broadcast` command.
- `commands.help.list` was replaced by `commands.help.entries` and `commands.info.lines` by `commands.info.content` (old keys are removed). New messages are added in Polish - translate them in `messages.yml` if needed.

**[Polski]**
- Przy pierwszym starcie plugin robi kopię `config.yml` i `messages.yml` (`config-backup-v1.yml`, `messages-backup-v1.yml`) i dopisuje wszystkie nowe opcje z komentarzami. Dzieje się to raz (`config-version: 2`).
- Twoje wartości zostają. Stary `time-formats.full` pozostaje zwykłym wzorcem - podmień go na nową sekcję smart z domyślnego configu, jeśli chcesz inteligentne formatowanie.
- Twoja lista kamieni milowych nie jest zmieniana. Stare komendy `broadcast ...` działają też na serwerach bez komendy `/broadcast`.
- `commands.help.list` zastąpiono `commands.help.entries`, a `commands.info.lines` - `commands.info.content` (stare klucze są usuwane). Nowe wiadomości są dopisywane po polsku.

### Plugin files / Pliki pluginu

| File | Description / Opis |
|------|--------------------|
| `config.yml` | Configuration / Konfiguracja |
| `messages.yml` | Messages / Wiadomości |
| `stats.db` | SQLite statistics / Statystyki (SQLite) |
| `sessions.yml` | Sessions saved on shutdown for `/reload` and relog protection (temporary) / Sesje zapisane przy wyłączeniu (tymczasowy) |
| `*-backup-v1.yml` | Backups made during the update / Kopie zapasowe z aktualizacji |

---

## Placeholders

All placeholders start with `%simplesession_`. Placeholders are safe to use asynchronously.

### Current session / Bieżąca sesja

| Placeholder | Description (English) | Opis (Polski) |
|-------------|----------------------|---------------|
| `%simplesession_seconds%` | Seconds component (0-59) | Sekundy (0-59) |
| `%simplesession_minutes%` | Minutes component (0-59) | Minuty (0-59) |
| `%simplesession_hours%` | Hours component (0-23) | Godziny (0-23) |
| `%simplesession_days%` | Days | Dni |
| `%simplesession_total_seconds%` | Whole session in seconds | Cała sesja w sekundach |
| `%simplesession_total_minutes%` | Whole session in minutes | Cała sesja w minutach |
| `%simplesession_total_hours%` | Whole session in hours | Cała sesja w godzinach |
| `%simplesession_total_days%` | Whole session in days | Cała sesja w dniach |
| `%simplesession_formatted%` | Session in `default-format` | Sesja w formacie `default-format` |
| `%simplesession_formatted_<format>%` | Session in any format from `time-formats` (e.g. `_full`, `_short`, `_compact`, `_long`, `_clock`, `_custom`) | Sesja w dowolnym formacie z `time-formats` |
| `%simplesession_rank%` | Position by current session (1 = longest) | Pozycja wg bieżącej sesji |

A player without a session (offline) is treated as 0 seconds; `rank` shows `placeholders.unranked`.
Gracz bez sesji (offline) ma 0 sekund; `rank` pokazuje `placeholders.unranked`.

### Statistics / Statystyki (`stats.enabled`)

| Placeholder | Description (English) | Opis (Polski) |
|-------------|----------------------|---------------|
| `%simplesession_stats_total%` | All-time play time | Łączny czas gry |
| `%simplesession_stats_record%` | Longest session | Najdłuższa sesja |
| `%simplesession_stats_today%` | Play time today | Czas gry dzisiaj |
| `%simplesession_stats_week%` | Play time this week | Czas gry w tym tygodniu |
| `%simplesession_stats_month%` | Play time this month | Czas gry w tym miesiącu |
| `%simplesession_stats_average%` | Average session | Średnia sesja |
| `%simplesession_stats_<x>_seconds%` | Any of the above in seconds | Dowolna z powyższych w sekundach |
| `%simplesession_stats_<x>_formatted_<format>%` | Any of the above in a chosen format | Dowolna z powyższych w wybranym formacie |
| `%simplesession_stats_sessions%` | Number of sessions | Liczba sesji |
| `%simplesession_stats_first_join%` | First join date | Data pierwszego wejścia |
| `%simplesession_stats_last_seen%` | Last seen date | Data ostatniej wizyty |
| `%simplesession_rank_<type>%` | Position in a leaderboard (`total`, `record`, `today`, `week`, `month`, `sessions`, `session`) | Pozycja w rankingu |

Time values use the format from `display.stats-placeholder`. Values are live (include not yet saved time). `stats_*` placeholders work for online players; for offline players (and when statistics are disabled) they return `placeholders.unavailable` - use the `top_<type>_*` placeholders for holograms.
Czas jest formatowany wg `display.stats-placeholder`. Wartości są na żywo. Placeholdery `stats_*` działają dla graczy online; dla graczy offline (i przy wyłączonych statystykach) zwracają `placeholders.unavailable` - do hologramów używaj placeholderów `top_<type>_*`.

`rank_<type>` for statistics uses the cached leaderboard, so it shows a position only within the first `leaderboard.placeholder-cache-size` players (otherwise `placeholders.unranked`).
`rank_<type>` dla statystyk korzysta z cache rankingu, więc pokazuje pozycję tylko w obrębie pierwszych `leaderboard.placeholder-cache-size` graczy.

### Leaderboards / Rankingi (holograms)

| Placeholder | Description (English) | Opis (Polski) |
|-------------|----------------------|---------------|
| `%simplesession_top_<n>_name%` | Name at position n (current sessions) | Nick na pozycji n (bieżące sesje) |
| `%simplesession_top_<n>_time%` | Session time at position n | Czas sesji na pozycji n |
| `%simplesession_top_<type>_<n>_name%` | Name at position n of a leaderboard | Nick na pozycji n rankingu |
| `%simplesession_top_<type>_<n>_time%` | Formatted value (`display.leaderboard-placeholder`) | Sformatowana wartość |
| `%simplesession_top_<type>_<n>_time_<format>%` | Value in a chosen format | Wartość w wybranym formacie |
| `%simplesession_top_<type>_<n>_value%` | Raw value (seconds / count) | Surowa wartość (sekundy / liczba) |

`<type>`: `session`, `total`, `record`, `today`, `week`, `month`, `sessions`.
Statistics leaderboards are cached (`leaderboard.placeholder-cache-size` positions, refreshed every `leaderboard.refresh-interval` seconds).
Rankingi ze statystyk są cache'owane (liczba pozycji i częstotliwość odświeżania w configu).

**Examples / Przykłady:**
- `%simplesession_top_1_name%` - longest current session / najdłuższa bieżąca sesja
- `%simplesession_top_total_1_name%` - most play time ever / najwięcej czasu gry
- `%simplesession_top_week_3_time%` - 3rd place this week / 3. miejsce w tym tygodniu
- `%simplesession_top_record_1_time_long%` - record in the "long" format / rekord w formacie "long"

---

## Commands / Komendy

| Command | Description | Opis | Permission |
|---------|-------------|------|------------|
| `/ss help` | Help (only allowed commands) | Pomoc (tylko dostępne komendy) | `simplesession.use` |
| `/ss info` | Plugin information | Informacje o pluginie | `simplesession.use` |
| `/ss top [type] [page]` | Leaderboard | Ranking | `simplesession.top` |
| `/ss check [player]` | Session + statistics (online or offline) | Sesja + statystyki (online i offline) | `simplesession.check`, `simplesession.check.others` |
| `/ss reset <player> <session\|total\|record\|sessions\|periods\|all>` | Reset session / statistics | Reset sesji / statystyk | `simplesession.reset` |
| `/ss set <player> <total\|record\|sessions> <value>` | Set a statistic | Ustaw statystykę | `simplesession.modify` |
| `/ss add <player> <total\|record\|sessions> <value>` | Add to a statistic | Dodaj do statystyki | `simplesession.modify` |
| `/ss take <player> <total\|record\|sessions> <value>` | Take from a statistic (min 0) | Odejmij od statystyki (min 0) | `simplesession.modify` |
| `/ss reload` | Reload configuration | Przeładuj konfigurację | `simplesession.reload` |
| `/ss debug` | Toggle debug mode | Przełącz tryb debugowania | `simplesession.debug` |

**Aliases / Aliasy:** `/simplesession`, `/ss`, `/session`

Values: seconds (`3600`) or units `w d h m s` (`1h30m`, `2d`). `session` restarts the current session of an online player, `periods` resets today/week/month. Commands work for offline players (statistics are read from the database).
Wartości: sekundy (`3600`) lub jednostki (`1h30m`, `2d`). `session` restartuje bieżącą sesję gracza online, `periods` resetuje dzień/tydzień/miesiąc. Komendy działają też dla graczy offline.

Leaderboard types for `/ss top`: `session` (default), `total`, `record`, `today`, `week`, `month`, `sessions`, e.g. `/ss top total 2`.
Typy rankingu: `session` (domyślny), `total`, `record`, `today`, `week`, `month`, `sessions`, np. `/ss top total 2`.

Note: commands that read the database (e.g. `/ss check` of an offline player, `/ss top total`) reply one tick later - in game and in the server console this works normally, but RCON clients do not receive such delayed replies.
Uwaga: komendy czytające bazę odpowiadają tick później - w grze i w konsoli działa to normalnie, ale klienci RCON nie otrzymują takich odpowiedzi.

## Permissions / Uprawnienia

| Permission | Default |
|------------|---------|
| `simplesession.use` | `true` |
| `simplesession.top` | `true` |
| `simplesession.check` | `true` |
| `simplesession.check.others` | `true` |
| `simplesession.reset` | `op` |
| `simplesession.modify` | `op` |
| `simplesession.reload` | `op` |
| `simplesession.debug` | `op` |
| `simplesession.admin` (all of the above / wszystkie powyższe) | `op` |

---

## Configuration / Konfiguracja

Every option is described with comments in `config.yml`. The most important sections:
Każda opcja jest opisana komentarzami w `config.yml`. Najważniejsze sekcje:

### Time formats / Formaty czasu

```yaml
time-formats:
  full:                       # smart format
    smart: true               # false -> uses "pattern"
    pattern: "{days} dni, {hours} godzin, {minutes} minut, {seconds} sekund"
    names: long               # preset from smart-formats.unit-names
    units: [days, hours, minutes, seconds]
    separator: ", "
    last-separator: " i "
    hide-zero: all            # all | leading | none
    max-units: 0              # 0 = all units
  compact:                    # "5h 21m"
    names: short
    unit-format: "{value}{unit}"
    separator: " "
    max-units: 2
  clock: "{total_hours}:{minutes_pad}:{seconds_pad}"   # plain pattern

default-format: "full"        # format of %simplesession_formatted%

smart-formats:
  enabled: true               # false = smart formatting off everywhere
  plural-rule: polish         # polish | english | none
  unit-names:                 # presets used by "names:" - long, short, english or your own
    long:
      hours: ["godzina", "godziny", "godzin"]   # [1, 2-4, 5+]
      # ...
    english:
      hours: ["hour", "hours"]
      # ...

display:                      # which format is used where
  leaderboard-command: compact
  leaderboard-placeholder: compact
  stats-placeholder: long
  check-command: full
  milestone: long
  relog-message: compact
```

Pattern variables / Zmienne wzorca: `{days} {hours} {minutes} {seconds}`, `{total_days} {total_hours} {total_minutes} {total_seconds}`, `{hours_pad} {minutes_pad} {seconds_pad}`, `{days_name} {hours_name} {minutes_name} {seconds_name}`.

Smart format options / Opcje formatu smart: `smart`, `pattern`, `names`, `units` (`weeks`, `days`, `hours`, `minutes`, `seconds`), `unit-format`, `separator`, `last-separator`, `max-units`, `hide-zero`, `zero`, `plural-rule`.

Bundled formats / Wbudowane formaty (3665 s):

| Format | Output / Wynik |
|--------|----------------|
| `full` | 1 godzina, 1 minuta i 5 sekund |
| `short` | 1h 1m 5s |
| `compact` | 1h 1m |
| `long` | 1 godzina i 1 minuta |
| `clock` | 1:01:05 |
| `custom` | 0d 1h 1m 5s |

### Relog protection / Ochrona przed relogiem

```yaml
session:
  keep-on-reload: true        # sessions survive /reload
relog-protection:
  enabled: true
  grace-period: 60            # seconds a player may be offline
  count-offline-time: false   # count the offline time into the session
  keep-after-restart: false   # also after a restart (within grace-period)
```

A restored session does not count as a new session in statistics and does not grant milestones again. The message `session.restored` in `messages.yml` (`{time}` = session, `{offline}` = time offline) is sent to the player - set it to `""` to disable.
Przywrócona sesja nie liczy się jako nowa w statystykach i nie przyznaje ponownie kamieni milowych. Gracz dostaje wiadomość `session.restored` (`{time}` = sesja, `{offline}` = czas offline) - ustaw `""`, aby ją wyłączyć.

### Statistics / Statystyki

```yaml
stats:
  enabled: true
  storage:
    file: "stats.db"
    table-prefix: "ss_"
  save-interval: 60
  timezone: "system"          # or e.g. "Europe/Warsaw"
  week-start: MONDAY
  date-format: "dd.MM.yyyy HH:mm"
  history-days: 400           # daily history kept (min 32, 0 = forever)
```

### Leaderboard / Ranking

```yaml
leaderboard:
  top-size: 10                # per page
  placeholder-cache-size: 100
  refresh-interval: 60
  title: "&6&l✪ TOP {size} &8- &e{type}"    # {size}, {type}, {page}, {pages}
  type-names: { session: "Bieżące sesje", total: "Łączny czas gry", ... }
  format:
    header: "..."
    separator: "..."
    line: "&7║ {medal} {rank}. {player} &7- {color}{time}"   # also {value}
    footer: "..."
    count: "{value}"                            # {time} of the "sessions" leaderboard
    navigation: "&7Strona &e{page}&7/&e{pages}{next}"
    next-page: " &8| &7Dalej: &e/ss top {type} {next_page}"
    medals: { first: "&6❶", second: "&7❷", third: "&c❸", other: "&8•" }
    colors: { first: "&6", second: "&7", third: "&c", other: "&f" }

placeholders:                 # texts returned by placeholders / teksty zwracane przez placeholdery
  empty-name: ""              # empty leaderboard position / pusta pozycja
  empty-time: ""
  unranked: "-"               # player not in a ranking / gracz poza rankingiem
  unavailable: "-"            # statistics disabled / not loaded / statystyki niedostępne
```

### Milestones / Kamienie milowe

```yaml
milestones:
  enabled: true
  check-interval: 10
  list:
    one_hour:
      time: 3600              # or "1h"
      message: "&6WOW! &ePełna godzina na serwerze!"
      broadcast: "&e{player} &7gra już &e1 godzinę!"
      permission: ""          # optional
      commands:
        - "give {player} diamond 1"            # console
        - "[player] spawn"
        - "[broadcast] &e{player} is great!"
        - "[message] &aText"
        - "[actionbar] &6+1 hour!"
        - "[title] &6WOW!;&e{time}"
        - "[sound] entity.player.levelup;1;1"
```

Placeholders: `{player}`, `{uuid}`, `{time}` (milestone time), `{session}` (current session) and PlaceholderAPI placeholders. Achieved milestones are kept when the session is restored.
Osiągnięte kamienie milowe są zachowywane przy przywróceniu sesji.

### messages.yml

All texts can be changed (default language: Polish); `{prefix}` works in every message, colors `&a` and `&#RRGGBB` are supported and an empty message (`""`) is not sent. Placeholders available in each message are described next to it in the file. `/ss check` is built from `commands.check.lines` and `commands.check.stats-lines` (the second list is shown only when statistics are enabled).
Wszystkie teksty można zmienić (domyślnie po polsku); `{prefix}` działa w każdej wiadomości, obsługiwane są kolory `&a` i `&#RRGGBB`, a pusta wiadomość nie jest wysyłana. Placeholdery dostępne w wiadomości są opisane obok niej w pliku. `/ss check` składa się z `commands.check.lines` i `commands.check.stats-lines` (druga lista tylko przy włączonych statystykach).

---

## Building / Budowanie

```bash
mvn clean package
```

The JAR is created in `target/`. Unit tests (formats, parser, SQLite storage, statistics) run during the build.
Plik JAR powstaje w `target/`. Testy jednostkowe uruchamiają się podczas budowania.

---

## License / Licencja

MIT - see [LICENSE](LICENSE).

## Author / Autor

**TremeQ**

---

## Version History / Historia Wersji

### 2.0.0
- Persistent statistics (SQLite): total, record, sessions, today/week/month, average, first join, last seen
- Leaderboards for every statistic with pages, new placeholders `stats_*`, `rank_<type>`, `top_<type>_<n>_*`
- Commands `/ss check`, `/ss reset`, `/ss set|add|take`, `/ss top [type] [page]`
- Relog protection, sessions kept after `/reload` (and optionally after a restart)
- Smart time formats with plural forms, named formats, `display` section, `formatted_<format>` placeholder
- Milestones: `broadcast`, `permission`, action prefixes, `{session}`, PlaceholderAPI, durations like `"30m"`
- Fixed: `{prefix}` was never replaced in messages
- Fixed: `{uuid}` in milestone messages and `{time}` in milestone commands were not replaced
- Fixed: default milestone commands used `/broadcast`, which does not exist on plain Spigot/Paper (old configs still work)
- Fixed: session started at MONITOR priority, so other plugins saw 0 in their join handlers
- Fixed: possible NullPointerException when placeholders were requested asynchronously
- Fixed: `/ss debug` rewrote the whole config.yml (quotes removed, manual edits overwritten)
- Fixed: `/ss help` showed admin commands to everyone, "Disabled" status was green, top placeholders limited to 10
- Leaderboard medals use characters available in the default Minecraft font
- Automatic config/messages update with backup
- Changed: default messages are in Polish; `commands.help.list` → `commands.help.entries`, `commands.info.lines` → `commands.info.content`
- Changed: permissions split into `simplesession.top`, `.check`, `.check.others`, `.reset`, `.modify`, `.reload`, `.debug` (`simplesession.admin` grants all)
- Changed: default milestone `check-interval` is 10 seconds

### 1.0.0
- Initial release

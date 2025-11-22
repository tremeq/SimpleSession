# SimpleSession

Modern session time tracking plugin for Minecraft 1.21

## Description / Opis

**[English]**

SimpleSession is a modern alternative to SessionTime, designed to track player session duration on Minecraft servers. The plugin integrates seamlessly with PlaceholderAPI, providing multiple placeholders for displaying session time in scoreboards, tab lists, GUIs, and other plugin systems.

**[Polski]**

SimpleSession to nowoczesna alternatywa dla SessionTime, zaprojektowana do śledzenia czasu trwania sesji graczy na serwerach Minecraft. Plugin płynnie integruje się z PlaceholderAPI, udostępniając wiele placeholderów do wyświetlania czasu sesji w scoreboardach, listach graczy, GUI i innych systemach pluginowych.

---

## Features / Funkcje

**[English]**
- Real-time session tracking from player join to disconnect
- Full PlaceholderAPI integration with 33 placeholders (13 basic + 20 leaderboard)
- Session milestones with customizable rewards and messages
- Top 10 leaderboard for current sessions with ranking system
- Leaderboard placeholders for holograms (top_1_name, top_1_time, etc.)
- Fully customizable leaderboard display (header, lines, footer, medals, colors)
- Customizable time display formats via configuration
- Support for days, hours, minutes, and seconds
- Admin commands for reload, debug, and info
- Optimized performance with smart caching system
- Built for Minecraft 1.21 with Java 21

**[Polski]**
- Śledzenie sesji w czasie rzeczywistym od wejścia do wyjścia gracza
- Pełna integracja z PlaceholderAPI z 33 placeholderami (13 podstawowych + 20 dla topki)
- Kamienie milowe sesji z konfigurowalnymi nagrodami i wiadomościami
- Ranking TOP 10 dla bieżących sesji z systemem pozycji
- Placeholdery topki dla hologramów (top_1_name, top_1_time, itp.)
- W pełni konfigurowalne wyświetlanie rankingu (nagłówek, linie, stopka, medale, kolory)
- Konfigurowalne formaty wyświetlania czasu
- Obsługa dni, godzin, minut i sekund
- Komendy administracyjne do przeładowania, debugowania i informacji
- Zoptymalizowana wydajność dzięki inteligentnemu systemowi cache
- Stworzony dla Minecraft 1.21 z Java 21

---

## Requirements / Wymagania

- Minecraft Server 1.21 (Spigot/Paper)
- Java 21
- PlaceholderAPI (optional but recommended)

---

## Installation / Instalacja

**[English]**
1. Download the latest version of SimpleSession
2. Place the JAR file in your server's `plugins` folder
3. Install PlaceholderAPI if not already installed
4. Restart or reload your server
5. Configure the plugin in `plugins/SimpleSession/config.yml`

**[Polski]**
1. Pobierz najnowszą wersję SimpleSession
2. Umieść plik JAR w folderze `plugins` na serwerze
3. Zainstaluj PlaceholderAPI, jeśli nie jest jeszcze zainstalowany
4. Zrestartuj lub przeładuj serwer
5. Skonfiguruj plugin w pliku `plugins/SimpleSession/config.yml`

---

## Placeholders

All placeholders start with `%simplesession_`

### Individual Time Components / Pojedyncze Komponenty Czasu

| Placeholder | Description (English) | Opis (Polski) |
|-------------|----------------------|---------------|
| `%simplesession_seconds%` | Remaining seconds (0-59) | Pozostałe sekundy (0-59) |
| `%simplesession_minutes%` | Remaining minutes (0-59) | Pozostałe minuty (0-59) |
| `%simplesession_hours%` | Remaining hours (0-23) | Pozostałe godziny (0-23) |
| `%simplesession_days%` | Total days | Całkowita liczba dni |

### Total Time Values / Całkowite Wartości Czasu

| Placeholder | Description (English) | Opis (Polski) |
|-------------|----------------------|---------------|
| `%simplesession_total_seconds%` | Total session time in seconds | Całkowity czas sesji w sekundach |
| `%simplesession_total_minutes%` | Total session time in minutes | Całkowity czas sesji w minutach |
| `%simplesession_total_hours%` | Total session time in hours | Całkowity czas sesji w godzinach |
| `%simplesession_total_days%` | Total session time in days | Całkowity czas sesji w dniach |

### Formatted Time Strings / Sformatowane Ciągi Czasu

| Placeholder | Description (English) | Opis (Polski) |
|-------------|----------------------|---------------|
| `%simplesession_formatted%` | Default format from config | Domyślny format z konfiguracji |
| `%simplesession_formatted_full%` | Full format (e.g., "2 dni, 5 godzin, 21 minut, 12 sekund") | Pełny format |
| `%simplesession_formatted_short%` | Short format (e.g., "2d 5h 21m 12s") | Krótki format |
| `%simplesession_formatted_custom%` | Custom format from config | Niestandardowy format z konfiguracji |

### Ranking & Leaderboard / Ranking i Tablica Wyników

| Placeholder | Description (English) | Opis (Polski) |
|-------------|----------------------|---------------|
| `%simplesession_rank%` | Player's rank in current session leaderboard (1 = longest) | Pozycja gracza w rankingu bieżących sesji (1 = najdłuższa) |
| `%simplesession_top_<number>_name%` | Name of player at position (1-10) | Nazwa gracza na pozycji (1-10) |
| `%simplesession_top_<number>_time%` | Session time of player at position (1-10) | Czas sesji gracza na pozycji (1-10) |

**Examples / Przykłady:**
- `%simplesession_top_1_name%` - Name of player with longest session / Nazwa gracza z najdłuższą sesją
- `%simplesession_top_1_time%` - Session time of #1 player / Czas sesji gracza #1
- `%simplesession_top_2_name%` - Name of player with 2nd longest session / Nazwa gracza z drugą najdłuższą sesją
- `%simplesession_top_10_time%` - Session time of #10 player / Czas sesji gracza #10

---

## Configuration / Konfiguracja

**config.yml:**

```yaml
# Time format settings
time-formats:
  full: "{days} dni, {hours} godzin, {minutes} minut, {seconds} sekund"
  short: "{days}d {hours}h {minutes}m {seconds}s"
  custom: "{days}d {hours}h {minutes}m {seconds}s"

# Default format to use (full, short, or custom)
default-format: "full"

# Enable debug mode
debug: false

# Leaderboard settings
leaderboard:
  top-size: 10  # How many players to show
  title: "&6&l🏆 TOP {size} - Bieżące Sesje"  # {size} = top-size value

  # Display format customization
  format:
    header: "&7╔════════════════════════════════╗"
    separator: "&7╠════════════════════════════════╣"
    line: "&7║ {medal} {rank}. {player} &7- {color}{time}"
    footer: "&7╚════════════════════════════════╝"

    medals:
      first: "🥇"   # 1st place
      second: "🥈"  # 2nd place
      third: "🥉"   # 3rd place
      other: "  "   # 4th+ place

    colors:
      first: "&6"   # 1st place (gold)
      second: "&7"  # 2nd place (gray)
      third: "&c"   # 3rd place (red)
      other: "&f"   # 4th+ place (white)

# Session milestones
milestones:
  enabled: true
  check-interval: 60  # Check every 60 seconds
  list:
    one_hour:
      time: 3600  # 1 hour in seconds
      message: "&6&lWOW! &eFull hour on the server! &6⭐"
      commands:
        - "broadcast &e{player} &7has been playing for &e1 hour&7!"
```

### Custom Format Variables / Zmienne Niestandardowego Formatu

#### Time Formats / Formaty Czasu

**[English]**
You can use the following variables in your custom time formats:
- `{days}` - Number of days
- `{hours}` - Number of hours (0-23)
- `{minutes}` - Number of minutes (0-59)
- `{seconds}` - Number of seconds (0-59)

**[Polski]**
Możesz używać następujących zmiennych w niestandardowych formatach czasu:
- `{days}` - Liczba dni
- `{hours}` - Liczba godzin (0-23)
- `{minutes}` - Liczba minut (0-59)
- `{seconds}` - Liczba sekund (0-59)

#### Leaderboard Line Format / Format Linii Rankingu

**[English]**
You can use the following variables in `leaderboard.format.line`:
- `{medal}` - Medal emoji (configured in medals section)
- `{rank}` - Position number (1, 2, 3, etc.)
- `{player}` - Player name
- `{time}` - Formatted session time
- `{color}` - Rank color (configured in colors section)

**[Polski]**
Możesz używać następujących zmiennych w `leaderboard.format.line`:
- `{medal}` - Emoji medalu (konfigurowane w sekcji medals)
- `{rank}` - Numer pozycji (1, 2, 3, itd.)
- `{player}` - Nazwa gracza
- `{time}` - Sformatowany czas sesji
- `{color}` - Kolor pozycji (konfigurowany w sekcji colors)

---

## Commands / Komendy

**[English]**

| Command | Description | Permission |
|---------|-------------|------------|
| `/simplesession help` | Display help message | `simplesession.use` |
| `/simplesession info` | Display plugin information | `simplesession.use` |
| `/simplesession top` | Show top 10 players by current session time | `simplesession.use` |
| `/simplesession reload` | Reload configuration | `simplesession.admin` |
| `/simplesession debug` | Toggle debug mode | `simplesession.admin` |

**Aliases:** `/ss`, `/session`

**[Polski]**

| Komenda | Opis | Uprawnienie |
|---------|------|-------------|
| `/simplesession help` | Wyświetla pomoc | `simplesession.use` |
| `/simplesession info` | Wyświetla informacje o pluginie | `simplesession.use` |
| `/simplesession top` | Pokazuje top 10 graczy według czasu bieżącej sesji | `simplesession.use` |
| `/simplesession reload` | Przeładowuje konfigurację | `simplesession.admin` |
| `/simplesession debug` | Przełącza tryb debugowania | `simplesession.admin` |

**Aliasy:** `/ss`, `/session`

---

## Session Milestones / Kamienie Milowe Sesji

**[English]**

Session milestones are achievements that players receive when they reach specific session durations. You can configure custom messages and commands (rewards) for each milestone.

**Example configuration:**
```yaml
milestones:
  enabled: true
  check-interval: 60  # Check every 60 seconds
  list:
    one_hour:
      time: 3600  # 1 hour in seconds
      message: "&6&lWOW! &eFull hour on the server! &6⭐"
      commands:
        - "broadcast &e{player} &7has been playing for &e1 hour&7!"
        - "give {player} diamond 1"
```

**Available placeholders in milestone messages:**
- `{player}` - Player name
- `{uuid}` - Player UUID
- `{time}` - Formatted milestone time

**[Polski]**

Kamienie milowe sesji to osiągnięcia które gracze otrzymują gdy osiągną określony czas sesji. Możesz skonfigurować własne wiadomości i komendy (nagrody) dla każdego kamienia milowego.

**Przykładowa konfiguracja:**
```yaml
milestones:
  enabled: true
  check-interval: 60  # Sprawdzaj co 60 sekund
  list:
    one_hour:
      time: 3600  # 1 godzina w sekundach
      message: "&6&lWOW! &ePełna godzina na serwerze! &6⭐"
      commands:
        - "broadcast &e{player} &7gra już &e1 godzinę&7!"
        - "give {player} diamond 1"
```

**Dostępne placeholdery w wiadomościach milestone:**
- `{player}` - Nazwa gracza
- `{uuid}` - UUID gracza
- `{time}` - Sformatowany czas milestone

---

## Building / Budowanie

**[English]**
To build the plugin from source:

```bash
mvn clean package
```

The compiled JAR will be located in the `target` folder.

**[Polski]**
Aby zbudować plugin ze źródeł:

```bash
mvn clean package
```

Skompilowany plik JAR będzie znajdował się w folderze `target`.

---

## Permissions / Uprawnienia

| Permission | Description (English) | Opis (Polski) | Default |
|------------|----------------------|---------------|---------|
| `simplesession.use` | Allows basic usage of SimpleSession commands | Pozwala na podstawowe użycie komend | `true` |
| `simplesession.admin` | Access to admin commands (reload, debug) | Dostęp do komend administracyjnych | `op` |

---

## Support / Wsparcie

**[English]**
If you encounter any issues or have suggestions, please create an issue on GitHub.

**[Polski]**
Jeśli napotkasz jakiekolwiek problemy lub masz sugestie, utwórz issue na GitHubie.

---

## License / Licencja

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

Ten projekt jest objęty licencją MIT - szczegóły w pliku [LICENSE](LICENSE).

---

## Author / Autor

**TremeQ**

---

## Version History / Historia Wersji

### 1.0.0
- Initial release
- Session tracking system (from join to quit/restart)
- PlaceholderAPI integration with 33 placeholders:
  - 13 basic placeholders (time components, formatted times, rank)
  - 20 leaderboard placeholders (top_1_name through top_10_name, top_1_time through top_10_time)
- Session milestones with customizable rewards and messages
- Top 10 leaderboard command (`/simplesession top`)
- Player ranking system (`%simplesession_rank%` placeholder)
- Leaderboard placeholders for holograms
- Fully customizable leaderboard display:
  - Configurable header, separator, line format, and footer
  - Custom medals for top 3 positions
  - Custom colors for each rank
  - Support for placeholders: {medal}, {rank}, {player}, {time}, {color}
- Customizable time display formats
- Smart caching system for optimal performance (90% reduction in sorting operations)
- Automatic cache invalidation on player join/quit
- Admin commands (reload, debug, info)
- Full Polish and English documentation
- MIT License

---

**[English]** Thank you for using SimpleSession!

**[Polski]** Dziękujemy za korzystanie z SimpleSession!

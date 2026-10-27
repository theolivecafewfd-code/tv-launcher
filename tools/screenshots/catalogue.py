"""Made-up demo catalogue for the screenshot run. Every title, channel and picture is invented."""

# (id, title, palette index, has backdrop, synopsis, genre, year, minutes, score)
MOVIES = [
    (2001, "The Long Summer", 0, True, "Three sisters run their late father's seaside hotel for one last season, and each has a reason to stay.", "Drama", 2026, 118, 8.3),
    (2002, "Northern Lights", 1, True, "A radio operator on a remote island picks up a message that shouldn't exist.", "Mystery, Drama", 2025, 104, 7.6),
    (2003, "Glass Harbour", 2, True, "A diver searching a sunken ferry finds the town's oldest secret.", "Thriller", 2024, 97, 7.1),
    (2004, "The Quiet Field", 3, True, "After a dry summer, a farming family discovers what lies under their land.", "Drama", 2023, 112, 6.9),
    (2005, "Midnight Ferry", 4, True, "Strangers on the last crossing of the night realise one of them is missing.", "Thriller", 2026, 95, 7.4),
    (2006, "Paper Kingdoms", 5, True, "Two rival bookshop owners are forced to share a shop for a year.", "Comedy", 2025, 101, 6.8),
    (2007, "A House by the Sea", 6, True, "An architect returns home to restore the house she once swore she'd never see again.", "Drama, Romance", 2024, 109, 7.2),
    (2008, "Iron Valley", 7, False, "A mining town votes on its own future, and a teacher stands in the way.", "Drama", 2022, 126, 7.0),
    (2009, "The Lantern Keeper", 8, True, "A boy and an old keeper keep a lighthouse running through the worst storm in a century.", "Family, Adventure", 2025, 92, 7.8),
    (2010, "Copper Sky", 9, True, "A test pilot pushes an experimental aircraft beyond its limits.", "Action", 2026, 121, 6.6),
    (2011, "Untitled Project", 10, False, "A film crew loses its director halfway through the shoot.", "Comedy", 2023, 88, 6.1),
    (2012, "Winter Orchard", 11, True, "A family gathers for the final apple harvest before the orchard is sold.", "Drama", 2024, 99, 7.3),
]

MOVIE_CATEGORIES = [
    ("10", "New this week", [2001, 2005, 2010, 2002, 2009, 2006, 2012, 2003]),
    ("11", "Drama", [2007, 2004, 2008, 2012, 2001, 2011, 2003]),
    ("12", "Thrillers", [2003, 2005, 2002, 2010]),
    ("13", "Family", [2009, 2006]),
]

# (id, title, palette, has backdrop, synopsis, genre, year, score, seasons)
SERIES = [
    (3001, "Harbour Lights", 12, True, "A small-town harbour master untangles a smuggling ring that reaches into her own family.", "Crime, Drama", 2025, 8.1, 2),
    (3002, "The Copper Line", 13, True, "Railway engineers race to finish a line across the mountains before winter.", "Drama", 2024, 7.5, 1),
    (3003, "Saltmarsh", 14, False, "A detective returns to the marshes where she grew up to solve a cold case.", "Crime", 2026, 7.9, 1),
    (3004, "Kitchen Table", 15, True, "Four flatmates, one kitchen, and a year of bad decisions.", "Comedy", 2023, 7.0, 1),
    (3005, "Far Country", 16, True, "Settlers on a distant colony learn they are not the first to arrive.", "Science fiction", 2026, 8.4, 1),
    (3006, "The Night Desk", 17, True, "The overnight team at a city newspaper chases one last big story.", "Drama", 2022, 7.2, 1),
]

SERIES_CATEGORIES = [
    ("20", "Drama series", [3001, 3002, 3006, 3005]),
    ("21", "Crime", [3003, 3001, 3006]),
    ("22", "Comedy", [3004]),
]

# Episode titles the way many providers send them: with the series name and codes in front.
HARBOUR_EPISODES = {
    1: [
        "Harbour Lights - S01E01 - The Stolen Eagle",
        "Harbour Lights - S01E02 - Low Tide",
        "S01E03 - Night Crossing",
        "Episode 4",
        "Harbour Lights 1x05 The Keeper",
        "Harbour Lights S01E06 Homecoming",
    ],
    2: [
        "Harbour Lights - S02E01 - Fog Bank",
        "Harbour Lights - S02E02 - The Ledger",
        "Harbour Lights - S02E03 - Breakwater",
        "Harbour Lights - S02E04 - Spring Tide",
    ],
}

EPISODE_PLOTS = [
    "A carved eagle vanishes from the harbour office, and Nora suspects someone on her own crew.",
    "The tide goes out further than anyone remembers, revealing a boat that was never reported missing.",
    "A ferry crossing in thick fog brings an unexpected passenger to town.",
    "Nora's brother asks for a favour she can't refuse.",
    "The old lighthouse keeper finally tells what he saw that night.",
    "A storm traps half the town in the harbour hotel.",
]

LIVE_GROUPS = [
    ("1", "Entertainment", ["Channel One", "Harbour TV", "Northside", "Studio 4", "Coastline", "City Live", "Drama Plus", "Classic Gold"]),
    ("2", "Sport", ["Sport Central", "Match Day", "Racing Live"]),
    ("3", "News", ["News Now", "World Report"]),
]

PROGRAMMES = [
    "Breakfast", "Morning Live", "The Garden Hour", "News at Noon", "Afternoon Film",
    "Quiz Time", "Local News", "Evening Kitchen", "The Big Match", "Late Review",
]

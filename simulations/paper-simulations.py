from model.util import get_default_config, split_by_percent
from simulation import run_simulation, make_dir

config = get_default_config()

# Baseline simulation:
config.POSTBLACKOUT_DAYS = list(range(8, 61))
config.REQUEST_CUTOFF_DAY = 15
run_simulation(config, "baseline")

# Varying Leech/Proactive percentage
config = get_default_config()
make_dir("data/runs/varyProactive")
for i in [0.05, 0.1, 0.25, 0.5, 0.75, 0.9]:
    proactive, leech = split_by_percent(i, 25000)
    config.NUM_REGULAR = proactive
    config.NUM_LEECH = leech
    run_simulation(config, f"varyProactive/{i}")

# Varying Adversaries
config = get_default_config()
make_dir("data/runs/varyAdversary")
for i in [0, 0.01, 0.02, 0.05, 0.1, 0.25, 0.5]:
    adversary, rest = split_by_percent(i, 25000)
    proactive, leech = split_by_percent(0.25, rest)
    config.NUM_ADVERSARY = adversary
    config.NUM_REGULAR = proactive
    config.NUM_LEECH = leech
    run_simulation(config, f"varyAdversary/{i}")

# Jamming Post-Blackout (without any adversary rating manipulation)
config = get_default_config()
make_dir("data/runs/jammingNoAdversary")
for i in [0, 10, 50, 100, 500, 1000, 5000, 10000, 15000, 20000]:
    config.JAM_TOP_K_LOCATIONS = i
    run_simulation(config, f"jammingNoAdversary/{i}")

# Jamming with adversary
config = get_default_config()
make_dir("data/runs/jammingWithAdversary")
for i in [500, 1000, 5000, 10000, 15000, 20000]:
    adversary, rest = split_by_percent(0.02, 25000)
    proactive, leech = split_by_percent(0.25, rest)
    config.NUM_ADVERSARY = adversary
    config.NUM_REGULAR = proactive
    config.NUM_LEECH = leech
    config.JAM_TOP_K_LOCATIONS = i
    run_simulation(config, f"jammingWithAdversary/{i}")

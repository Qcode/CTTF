import subprocess
import time
from concurrent.futures import ProcessPoolExecutor as Pool
from copy import deepcopy
from model.util import get_default_config, split_by_percent
from model.config import Config, FetchingType, ModelType
from simulation import run_simulation, make_dir
import pickle

class ConfigNamePair:
    def __init__(self, config: Config, name: str):
        self.config = deepcopy(config)
        self.name = name


def do_simulation(config_pair):
    run_simulation(config_pair.config, config_pair.name)


if __name__ == "__main__":
    all_configs = []

    config = get_default_config()
    '''
    # Baseline simulation:
    config.POSTBLACKOUT_DAYS = list(range(8, 61))
    config.REQUEST_CUTOFF_DAY = 15
    all_configs.append(ConfigNamePair(config, "baseline"))

    # Baseline without interpolation
    config.INTERPOLATED = False
    all_configs.append(ConfigNamePair(config, "baselineNoInterpolation"))

    # Varying Leech/Proactive percentage
    config = get_default_config()
    make_dir("data/runs/varyProactive")
    for i in [0.05, 0.1, 0.25, 0.5, 0.75, 0.9]:
        proactive, leech = split_by_percent(i, 25000)
        config.NUM_REGULAR = proactive
        config.NUM_LEECH = leech
        all_configs.append(ConfigNamePair(config, f"varyProactive/{i}"))
    '''
    '''
    # Varying Adversaries
    config = get_default_config()
    make_dir("data/runs/varyAdversary-asatisfy")
    for i in [0, 0.01, 0.02, 0.05, 0.1, 0.25]:
        adversary, rest = split_by_percent(i, 25000)
        proactive, leech = split_by_percent(0.25, rest)
        config.NUM_ADVERSARY = adversary
        config.NUM_REGULAR = proactive
        config.NUM_LEECH = leech
        all_configs.append(ConfigNamePair(config, f"varyAdversary-asatisfy/{i}"))
    '''
    '''
    # Jamming Post-Blackout (without any adversary rating manipulation)
    config = get_default_config()
    make_dir("data/runs/jammingNoAdversary")
    for i in [0, 10, 100, 1000, 10000, 20000]:
        config.JAM_TOP_K_LOCATIONS = i
        all_configs.append(ConfigNamePair(config, f"jammingNoAdversary/{i}"))

    # Jamming with adversary
    config = get_default_config()
    make_dir("data/runs/jammingWithAdversary")
    for i in [0, 0.01, 0.02, 0.05, 0.1, 0.25]:
        for jam in [0, 10, 100, 1000, 10000, 20000]:
            adversary, rest = split_by_percent(i, 25000)
            proactive, leech = split_by_percent(0.25, rest)
            config.NUM_ADVERSARY = adversary
            config.NUM_REGULAR = proactive
            config.NUM_LEECH = leech
            config.JAM_TOP_K_LOCATIONS = jam
            all_configs.append(ConfigNamePair(config, f"jammingWithAdversary/{i}-{jam}"))

    # Cache start up time
    config = get_default_config()
    make_dir("data/runs/cacheStartUp")
    for startup_days in [1, 3, 5, 7, 9, 11, 13]:
        config.PREBLACKOUT_DAYS = list(range(1, startup_days + 1))
        config.POSTBLACKOUT_DAYS = list(range(startup_days + 1, startup_days + 8))
        config.REQUEST_CUTOFF_DAY = startup_days + 2
        all_configs.append(ConfigNamePair(config, f"cacheStartUp/{startup_days}"))


    # Different contact probabilities
    config = get_default_config()
    make_dir("data/runs/contactProbability")
    for i in [1, 0.75, 0.5, 0.25, 0.1, 0.05, 0.01]:
        config.CONTACT_PROBABILITY = i
        all_configs.append(ConfigNamePair(config, f"contactProbability/{i}"))

    # Different proportions of ranked/uniform
    config = get_default_config()
    make_dir("data/runs/ranked-uniform")
    for ranked in [500, 750, 1000, 2000]:
        for uniform in [0, 0.1, 0.2, 0.3]:
            config.UNIFORM_RATINGS = uniform
            config.PAGES_RANKED = ranked
            all_configs.append(
                ConfigNamePair(config, f"ranked-uniform/{ranked}-{uniform}")
            )

    # EPIDEMIC
    config = get_default_config()
    make_dir("data/runs/epidemic")
    for adversaryPercent in [0, 0.01, 0.02, 0.05, 0.1, 0.25]:
        for spamMultiplier in [1, 2, 4, 8, 16, 32, 64, 128, 256, 512]:
            if adversaryPercent == 0 and spamMultiplier != 1:
                continue
            adversary, rest = split_by_percent(adversaryPercent, 25000)
            proactive, leech = split_by_percent(0.25, rest)
            config.NUM_ADVERSARY = adversary
            config.NUM_LEECH = leech
            config.NUM_REGULAR = proactive
            config.FETCHING_TYPE = FetchingType.EPIDEMIC
            config.ADVERSARY_FORCE_MULTIPLIER = spamMultiplier
            all_configs.append(
                ConfigNamePair(config, f"epidemic/{adversaryPercent}-{spamMultiplier}"))

    config = get_default_config()
    make_dir("data/runs/grid")
    for adversaryPercent in [0, 0.01, 0.02, 0.05, 0.1, 0.25]:
        for jam in [0, 10, 50, 100, 300]:
            adversary, rest = split_by_percent(adversaryPercent, 600)
            proactive, leech = split_by_percent(0.25, rest)
            config.SIMULATION_TYPE = ModelType.GRID
            config.NUM_ADVERSARY = adversary
            config.NUM_LEECH = leech
            config.NUM_REGULAR = proactive
            config.TOTAL_USERS = 600
            config.GRID_SIZE = 25
            config.JAM_TOP_K_LOCATIONS = jam
            all_configs.append(ConfigNamePair(config, f"grid/a{adversaryPercent}-j{jam}"))'''

    config = get_default_config()
    make_dir("data/runs/stalking")
    for adversaryPercent in [0, 0.01, 0.02, 0.05, 0.1, 0.25]:
        for pow_limit in [30, 60, 90, 120, 360, 720, 1800]:
            config.STALKING = True
            adversary, rest = split_by_percent(adversaryPercent, 25000)
            proactive, leech = split_by_percent(0.25, rest)
            config.NUM_ADVERSARY = adversary
            config.NUM_LEECH = leech
            config.NUM_REGULAR = proactive
            config.POW_LIMIT = pow_limit
            all_configs.append(ConfigNamePair(config, f"stalking/{adversaryPercent}-{pow_limit}"))

    print("Created all configs")
    print(len(all_configs))
    processes = []
    for config in all_configs:
        config_dir_name = f"data/runs/{config.name}"
        make_dir(config_dir_name)
        config_file_name = f"{config_dir_name}/config"
        config_log_name = f"{config_dir_name}/log"
        with open(config_file_name, "wb") as f:
            pickle.dump(config.config, f)
        with open(config_log_name, "w") as f:
            cmd = f"python3 simulation.py {config_file_name} {config.name}"
            proc = subprocess.Popen(cmd, shell=True, stdout=f, stderr=subprocess.STDOUT)
            print("STARTING")
            print(config.config)
            processes.append(proc)
        while len(processes) >= 224:
            for process in processes:
                if process.poll() is not None:
                    processes.remove(process)
                    break
            else:
                time.sleep(1)


    for i, p in enumerate(processes):
        p.wait()
        print(f"Process {i}, {all_configs[i].name} finished")

import os
import sys
from multiprocessing import Pool, cpu_count
import pickle

import pandas as pd
import numpy as np
from tqdm import tqdm
from model.config import ModelType, FetchingType
from model.util import get_default_config
from model.ratings import (
    get_truth_rankings,
    get_truth_probability,
    get_adversary_preferences,
)
from model.simulation import (
    generate_days,
    simulate_pre_blackout,
    simulate_post_blackout,
    simulate_epidemic_routing,
)
from model.user import User, get_user_designations, UserType


def make_dir(name):
    if not os.path.isdir(name):
        os.mkdir(name)


def create_user(args):
    config = args[0]
    user_designation = args[1]
    i = args[2]
    return User(
        config,
        user_designation,
        i,
        preferences=(
            get_adversary_preferences(config)
            if user_designation == UserType.ADVERSARY
            else None
        ),
    )


def run_simulation(config_arg=None, run_name=None):
    config = config_arg if config_arg is not None else get_default_config()
    np.random.seed(config.SEED)

    make_dir("data")
    make_dir("plots")
    make_dir("data/leech")
    make_dir("data/runs")
    make_dir(f"data/user-preferences-s{config.SEED}")

    the_truth_probability = get_truth_probability(config.PAGE_COUNT)

    user_designations = get_user_designations(config)
    args = [(config, user_designations[i], i) for i in range(config.TOTAL_USERS)]

    with Pool(processes=cpu_count()) as pool:
        users = list(
            tqdm(
                pool.imap(create_user, args),
                total=config.TOTAL_USERS,
                desc="Creating users",
            )
        )

    the_dataset = None
    print("Loading dataset")
    jammed = []
    if config.SIMULATION_TYPE == ModelType.JAPAN:
        the_dataset = pd.read_csv("datasets/yjmob100k-dataset2-interpolated.csv")
        if config.JAM_TOP_K_LOCATIONS > 0:
            top_poi_locations = pd.read_csv("datasets/processed_poi.csv")
            top_poi_locations = top_poi_locations.sort_values(
                "POI_count", ascending=False
            )
            top_poi_locations = top_poi_locations.reset_index(drop=True)
            first_k_rows = top_poi_locations.head(config.JAM_TOP_K_LOCATIONS)
            print(first_k_rows)
            first_k_rows = first_k_rows.drop("POI_count", axis=1)
            jammed = set(first_k_rows.itertuples(index=False, name=None))
            print(jammed)
    elif config.SIMULATION_TYPE == ModelType.GRID:
        the_dataset = generate_days(config)

    if config.FETCHING_TYPE == FetchingType.CTTF:
        simulate_pre_blackout(config, the_dataset, users)

    print("Storing pages")
    for user in tqdm(users):
        user.store_pages()

    if config.FETCHING_TYPE == FetchingType.CTTF:
        simulate_post_blackout(
            config, the_dataset, users, the_truth_probability, jammed
        )
    else:
        simulate_epidemic_routing(
            config, the_dataset, users, the_truth_probability, jammed
        )

    print("Saving data")
    next_run = run_name
    if run_name is None:
        prior_runs = list(
            filter(lambda name: name != ".DS_Store", os.listdir("data/runs"))
        )
        next_run = max(map(int, prior_runs)) + 1 if prior_runs else 1

    os.mkdir(f"data/runs/{next_run}")

    with open(f"data/runs/{next_run}/users", "wb") as f:
        pickle.dump(users, f)
    with open(f"data/runs/{next_run}/config", "wb") as f:
        pickle.dump(config, f)


if __name__ == "__main__":
    config_sys_arg = None
    if len(sys.argv) > 1:
        with open(sys.argv[1], "rb") as f:
            print(f"Opening {sys.argv[1]}")
            config_sys_arg = pickle.load(f)
    run_simulation(config_sys_arg)

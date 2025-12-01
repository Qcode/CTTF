import sys
import os
import pickle
from scipy.sparse import csr_array
from model.user import UserType


def get_config(the_run = None):
    runs = list(filter(lambda name: name != ".DS_Store", os.listdir("data/runs")))
    if the_run is None:
        the_run = sys.argv[1] if len(sys.argv) > 1 else max(map(int, runs))
        
    with open(f"data/runs/{the_run}/config", "rb") as f:
        config = pickle.load(f)
        if not hasattr(config, "JAM_TOP_K_LOCATIONS"):
            config.JAM_TOP_K_LOCATIONS = 0
        print("CONFIG")
        print(config)
        return config, the_run


def make_plot_dir(the_run):
    if not os.path.isdir(f"plots/{the_run}"):
        os.mkdir(f"plots/{the_run}")


def get_nonzero_preferences(config, users):
    all_preferences = csr_array((1, config.PAGE_COUNT))

    for i in range(config.TOTAL_USERS):
        if users[i].user_type == UserType.NORMAL:
            all_preferences += users[i].preferences

    return all_preferences.count_nonzero()


def get_stored_across_all(config, users):
    stored_set = set()

    for i in range(config.TOTAL_USERS):
        stored_set.update(users[i].stored_pages)
    return stored_set


def get_leech_info(config, users):
    overall_leech_probabilities = csr_array((1, config.PAGE_COUNT))
    leeches_with_interactions = 0
    average_pages_seen = 0

    for i in range(config.TOTAL_USERS):
        if (
            users[i].user_type == UserType.LEECH
            and users[i].computed_preferences is not None
        ):
            leeches_with_interactions += 1
            overall_leech_probabilities += users[i].computed_preferences
            average_pages_seen += users[i].computed_preferences.size

    return (
        overall_leech_probabilities / leeches_with_interactions,
        leeches_with_interactions,
        average_pages_seen / leeches_with_interactions,
    )


def get_total_requests(users):
    total_requests = 0
    for user in users:
        total_requests += len(user.requested_pages)
    return total_requests


def get_total_resolved(users):
    total_resolved = 0
    for user in users:
        for request in user.requested_pages:
            if request.is_resolved():
                total_resolved += 1
    return total_resolved


def get_total_with_interactions(users):
    total_with_interactions = 0
    for user in users:
        for request in user.requested_pages:
            if request.has_interacted:
                total_with_interactions += 1
    return total_with_interactions

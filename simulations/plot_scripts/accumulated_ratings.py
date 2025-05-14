import pickle
import multiprocessing as mp
from tqdm import tqdm
from scipy.sparse import csr_array
from scipy.spatial.distance import jensenshannon
import matplotlib.pyplot as plt
from plot_scripts.util import get_leech_info
from model.ratings import get_truth_probability, ranking_to_probability_dist_sparse
from model.user import UserType


truth_probability = get_truth_probability(10**6)


def compute_jsd(user):
    if user.user_type == UserType.LEECH and user.computed_preferences is not None:
        jsd = jensenshannon(
            truth_probability,
            ranking_to_probability_dist_sparse(user.computed_preferences)
            .toarray()
            .flatten(),
        )
        return jsd
    return None


if __name__ == "__main__":
    runs = [1, 2, 8, 9]
    num_pre_blackout_days = []
    average_pages_seen_y = []
    many_jsd = []

    for run in runs:
        with open(f"data/runs/{run}/config", "rb") as f:
            config = pickle.load(f)
            if not hasattr(config, "JAM_TOP_K_LOCATIONS"):
                config.JAM_TOP_K_LOCATIONS = 0
            print("CONFIG")
            print(config)

        print("Loading Users")
        with open(f"data/runs/{run}/users", "rb") as f:
            users = pickle.load(f)

        with mp.Pool(mp.cpu_count()) as pool:
            results = list(tqdm(pool.imap(compute_jsd, users), total=len(users)))

        valid_results = [r for r in results if r is not None]
        average_jsd = sum(valid_results) / len(valid_results)

        _, leeches_with_interactions, average_pages_seen = get_leech_info(config, users)

        truth_probability = get_truth_probability(config.PAGE_COUNT)

        print(average_jsd)
        many_jsd.append(average_jsd)

        num_pre_blackout_days.append(len(config.PREBLACKOUT_DAYS))
        average_pages_seen_y.append(average_pages_seen)

    print(average_pages_seen_y)
    print(many_jsd)
    plt.title("Caching Time vs. Pages Seen")
    plt.plot(num_pre_blackout_days, average_pages_seen_y, "bo")
    plt.xlabel("Number of days pre-blackout")
    plt.ylabel("Average Leech Ratings Encountered")
    plt.savefig("plots/caching-vs-pages-seen.pdf", format="pdf", bbox_inches="tight")

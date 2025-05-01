from tqdm import tqdm
import numpy as np
from multiprocessing import Pool
from scipy.sparse import csr_array
from model.util import get_default_config
from model.user import User, get_user_designations, UserType
from model.ratings import (
    generate_individual_preferences,
    get_page_probability,
    zipf_to_ranking,
)

config = get_default_config()
config.NUM_ADVERSARY = 0
config.NUM_LEECH = 18750
config.NUM_REGULAR = 6250

the_zipf = get_page_probability(config.PAGE_COUNT)
the_truth_rankings = zipf_to_ranking(the_zipf)
the_truth_probability = the_zipf / np.sum(the_zipf)


def _wrapper_generate(config):
    return generate_individual_preferences(
        config, the_truth_probability, the_truth_rankings
    )


if __name__ == "__main__":
    for pages_ranked in tqdm([100, 300, 400, 500, 750, 1000]):
        config.PAGES_RANKED = pages_ranked
        for uniform_rating_percent in tqdm([0, 0.1, 0.2, 0.3, 0.4, 0.5]):
            config.UNIFORM_RATINGS = uniform_rating_percent

            with Pool(processes=6) as pool:
                preferences_list = list(
                    tqdm(
                        pool.imap_unordered(
                            _wrapper_generate, [config] * config.NUM_REGULAR
                        ),
                        total=config.NUM_REGULAR,
                        desc=f"Ranked = {pages_ranked}, Uniform={uniform_rating_percent}",
                    )
                )

            all_preferences = sum(
                preferences_list, start=csr_array((1, config.PAGE_COUNT))
            )

            print(f"Ranked: {pages_ranked}")

            print(f"Uniform ratings: {uniform_rating_percent}")
            print(f"Nonzero preferences: {all_preferences.count_nonzero()}")

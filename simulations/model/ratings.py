from scipy.sparse import csr_array
import numpy as np
from model.config import AttenuatingNoiseType
from model.util import split_by_percent


def generate_zipf_mandelbrot(n, s, q):
    indices = np.arange(1, n + 1) + q
    weights = 1 / (indices**s)
    return weights


def get_page_probability(page_count):
    page_probability = generate_zipf_mandelbrot(page_count, 1, 0)
    return page_probability


def zipf_to_ranking(a):
    to_return = 10 * (2.34 * (a**0.039) - 1.36)
    return to_return


def ranking_to_zipf(a):
    to_return = ((a + 10 * 1.36) / (10 * 2.34)) ** (1 / 0.039)
    return to_return


def ranking_to_probability_dist(a):
    zipf = ranking_to_zipf(a)
    zipf /= np.sum(zipf)
    return zipf


def ranking_to_zipf_sparse(a):
    a = a.copy()
    a.data = ((a.data + 10 * 1.36) / (10 * 2.34)) ** (1 / 0.039)
    return a


def get_truth_rankings(PAGE_COUNT):
    the_zipf = get_page_probability(PAGE_COUNT)
    return zipf_to_ranking(the_zipf)


def get_truth_probability(PAGE_COUNT):
    the_zipf = get_page_probability(PAGE_COUNT)
    return the_zipf / np.sum(the_zipf)


def ranking_to_probability_dist_sparse(a):
    zipf = ranking_to_zipf_sparse(a)
    total = zipf.sum()
    if total > 0:
        zipf.data /= total
    return zipf


def noise_preferences(config, the_truth_rankings):
    std_dev = config.INDIVIDUAL_NOISE
    if config.ATTENUATING_NOISE == AttenuatingNoiseType.LINEAR:
        std_dev = config.INDIVIDUAL_NOISE * (
            np.arange(config.PAGE_COUNT, 0, -1)
            / np.full(config.PAGE_COUNT, config.PAGE_COUNT)
        )
    elif config.ATTENUATING_NOISE == AttenuatingNoiseType.EXPONENTIAL:
        b = np.log(config.INDIVIDUAL_NOISE / 0.01) / (config.PAGE_COUNT - 1)
        std_dev = config.INDIVIDUAL_NOISE * np.exp(-b * np.arange(config.PAGE_COUNT))
    noise = np.random.normal(0, std_dev, size=config.PAGE_COUNT)
    return np.clip(the_truth_rankings + noise, 0, 10)


def generate_individual_preferences(
    config,
    truth_probability,
    truth_rankings,
):
    uniform_page_count, zipf_page_count = split_by_percent(
        config.UNIFORM_RATINGS, config.PAGES_RANKED
    )
    zipf_indices = np.random.choice(
        config.PAGE_COUNT, size=zipf_page_count, p=truth_probability, replace=False
    )
    uniform_probabilities = np.full(
        config.PAGE_COUNT, 1 / (config.PAGE_COUNT - zipf_page_count)
    )
    uniform_probabilities[zipf_indices] = 0
    uniform_indices = np.random.choice(
        config.PAGE_COUNT,
        size=uniform_page_count,
        p=uniform_probabilities,
        replace=False,
    )
    chosen_indices = np.concatenate((zipf_indices, uniform_indices))
    index_values = noise_preferences(config, truth_rankings)[chosen_indices]

    sparse_vec = csr_array(
        (index_values, (np.zeros_like(chosen_indices, dtype=int), chosen_indices)),
        shape=(1, config.PAGE_COUNT),
    )
    return sparse_vec


def get_adversary_preferences(config):
    good_decrease, bad_increase = split_by_percent(
        config.ADVERSARY_GOOD_DECREASE_SPLIT, config.ADVERSARY_RATINGS
    )
    indices = np.concatenate(
        (
            np.array(np.arange(good_decrease)),
            (np.array(np.arange(config.PAGE_COUNT - bad_increase, config.PAGE_COUNT))),
        )
    )
    values = np.concatenate((np.zeros(good_decrease), np.full(bad_increase, 10)))
    sparse_vec = csr_array(
        (values, (np.zeros_like(indices, dtype=int), indices)),
        shape=(1, config.PAGE_COUNT),
    )
    return sparse_vec

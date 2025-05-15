from model.config import Config, AttenuatingNoiseType, ModelType, FetchingType


def split_by_percent(percent, total):
    first = round(percent * total)
    second = total - first
    return first, second


def get_default_config():

    return Config(
        PAGE_COUNT=10**6,
        PAGES_STORED=1200,
        PAGES_RANKED=500,
        UNIFORM_RATINGS=0,
        INDIVIDUAL_NOISE=0.5,
        PAGE_REQUEST_PROBABILITY=0.25,
        CONTACT_PROBABILITY=1,
        NUM_REGULAR=6250,
        NUM_LEECH=18750,
        NUM_ADVERSARY=500,
        ADVERSARY_FORCE_MULTIPLIER=1,
        ADVERSARY_RATINGS=1000,
        ADVERSARY_GOOD_DECREASE_SPLIT=0.5,
        SEED=0,
        PREBLACKOUT_DAYS=[1, 2, 3, 4, 5, 6, 7],
        POSTBLACKOUT_DAYS=[8, 9, 10, 11, 12, 13, 14, 15],
        REQUEST_CUTOFF_DAY=9,
        REQUEST_CUTOFF_TIMESTEP=0,
        ATTENUATING_NOISE=AttenuatingNoiseType.EXPONENTIAL,
        SIMULATION_TYPE=ModelType.JAPAN,
        GRID_SIZE=200,
        MOVEMENT_DISTANCE=2,
        FORWARDING_LIMIT=4,
        JAM_TOP_K_LOCATIONS=0,
        FETCHING_TYPE=FetchingType.CTTF,
    )

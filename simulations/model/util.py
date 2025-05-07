from model.config import Config, AttenuatingNoiseType, ModelType


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
        NUM_REGULAR=6125,
        NUM_LEECH=18375,
        NUM_ADVERSARY=500,
        ADVERSARY_FORCE_MULTIPLIER=1,
        ADVERSARY_RATINGS=1000,
        ADVERSARY_GOOD_DECREASE_SPLIT=0.5,
        SEED=0,
        PREBLACKOUT_DAYS=[1],
        POSTBLACKOUT_DAYS=[2],
        REQUEST_CUTOFF_DAY=3,
        REQUEST_CUTOFF_TIMESTEP=0,
        ATTENUATING_NOISE=AttenuatingNoiseType.EXPONENTIAL,
        SIMULATION_TYPE=ModelType.JAPAN,
        GRID_SIZE=200,
        MOVEMENT_DISTANCE=2,
        FORWARDING_LIMIT=4,
    )

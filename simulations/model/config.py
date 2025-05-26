from dataclasses import dataclass, field
from enum import Enum


class AttenuatingNoiseType(Enum):
    LINEAR = 1
    EXPONENTIAL = 2
    NONE = 3


class ModelType(Enum):
    GRID = 1
    JAPAN = 2


class FetchingType(Enum):
    CTTF = 1
    EPIDEMIC = 2


@dataclass
class Config:
    PAGE_COUNT: int
    PAGES_STORED: int
    PAGES_RANKED: int
    UNIFORM_RATINGS: float
    INDIVIDUAL_NOISE: 0.5

    PAGE_REQUEST_PROBABILITY: float
    CONTACT_PROBABILITY: float

    NUM_REGULAR: int
    NUM_LEECH: int
    NUM_ADVERSARY: int

    ADVERSARY_FORCE_MULTIPLIER: int
    ADVERSARY_RATINGS: int
    ADVERSARY_GOOD_DECREASE_SPLIT: float

    SEED: int

    PREBLACKOUT_DAYS: list[int]
    POSTBLACKOUT_DAYS: list[int]

    REQUEST_CUTOFF_DAY: int
    REQUEST_CUTOFF_TIMESTEP: int

    ATTENUATING_NOISE: AttenuatingNoiseType

    SIMULATION_TYPE: ModelType

    GRID_SIZE: int
    TOTAL_USERS: int = field(init=False)

    MOVEMENT_DISTANCE: int

    FORWARDING_LIMIT: int

    JAM_TOP_K_LOCATIONS: int

    FETCHING_TYPE: FetchingType

    INTERPOLATED: bool
    SPACE_FOR_FORWARDING: int

    def __post_init__(self):
        self.TOTAL_USERS = self.NUM_REGULAR + self.NUM_LEECH + self.NUM_ADVERSARY

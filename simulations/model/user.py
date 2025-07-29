from enum import Enum
import os
import numpy as np
from scipy.sparse import csr_array, load_npz, save_npz
from model.ratings import (
    generate_individual_preferences,
    ranking_to_probability_dist_sparse,
    get_truth_probability,
    get_truth_rankings,
)


class UserType(Enum):
    NORMAL = 1
    LEECH = 2
    ADVERSARY = 3


class PageRequest:
    def __init__(self, index, day, timestep):
        self.index = index
        self.started_day = day
        self.started_timestep = timestep

        self.resolved_days = []
        self.resolved_timesteps = []
        self.resolved_by = []
        self.has_interacted = False

    def resolve(self, day, timestep, resolved_by):
        if not self.is_full():
            self.resolved_days.append(day)
            self.resolved_timesteps.append(timestep)
            self.resolved_by.append(resolved_by)

    def is_resolved(self):
        return len(self.resolved_days) > 0

    def is_full(self):
        return len(self.resolved_days) >= 10


class User:
    def __init__(
        self,
        config,
        user_type,
        index,
        preferences=None,
    ):
        self.config = config
        self.index = index
        self.user_type = user_type
        self.preferences = preferences
        self.original_preferences = preferences
        self.reset()

        self.requested_pages = []
        self.forwarding_requests = []
        self.forwarding_responses = []
        self.stored_pages = None

    def reset(self):
        self.encountered = 1
        if self.user_type == UserType.NORMAL and self.original_preferences is None:
            file_location = f"data/user-preferences-s{self.config.SEED}/preferences-a{self.config.ATTENUATING_NOISE}-ur{self.config.UNIFORM_RATINGS}-{self.index}.npz"
            if False: #os.path.isfile(file_location):
                self.preferences = load_npz(file_location)
            else:
                truth_probability = get_truth_probability(self.config.PAGE_COUNT)
                truth_rankings = get_truth_rankings(self.config.PAGE_COUNT)
                self.preferences = generate_individual_preferences(
                    self.config, truth_probability, truth_rankings
                )
                #save_npz(
                #    f"data/user-preferences-s{self.config.SEED}/preferences-a{self.config.ATTENUATING_NOISE}-ur{self.config.UNIFORM_RATINGS}-{self.index}.npz",
                #    self.preferences,
                #)
        else:
            self.preferences = self.original_preferences

        self.computed_preferences = self.preferences
        self.num_rankings = csr_array((1, self.config.PAGE_COUNT))
        if self.preferences is not None:
            one_hot_vector = self.preferences.copy()
            repeat = (
                self.config.ADVERSARY_FORCE_MULTIPLIER
                if self.user_type == UserType.ADVERSARY
                else 1
            )
            one_hot_vector.data[:] = repeat
            self.one_hot_vector = one_hot_vector

    def store_pages(self):
        self.requested_pages = []
        self.forwarding_requests = set()
        self.forwarding_responses = set()

        if self.user_type == UserType.ADVERSARY or self.computed_preferences is None:
            self.stored_pages = set()
            return

        probability = ranking_to_probability_dist_sparse(self.computed_preferences)
        if probability.count_nonzero() < self.config.PAGES_STORED:
            self.stored_pages = set(probability.nonzero()[1])
        else:
            self.stored_pages = set(
                np.random.choice(
                    probability.indices,
                    size=self.config.PAGES_STORED,
                    p=probability.data,
                    replace=False,
                )
            )

    def listify(self):
        for attr, value in self.__dict__.items():
            if isinstance(value, set):
                setattr(self, attr, list(value))


def get_user_designations(config):
    if False:#os.path.isfile(
#        f"data/user-designations-s{config.SEED}-n{config.NUM_REGULAR}-l{config.NUM_LEECH}-a{config.NUM_ADVERSARY}.npy"
#    ):
        return np.load(
            f"data/user-designations-s{config.SEED}-n{config.NUM_REGULAR}-l{config.NUM_LEECH}-a{config.NUM_ADVERSARY}.npy",
            allow_pickle=True,
        )

    user_designations = (
        [UserType.NORMAL] * config.NUM_REGULAR
        + [UserType.LEECH] * config.NUM_LEECH
        + [UserType.ADVERSARY] * config.NUM_ADVERSARY
    )
    #np.random.shuffle(user_designations)
    #np.save(
    #    f"data/user-designations-s{config.SEED}-n{config.NUM_REGULAR}-l{config.NUM_LEECH}-a{config.NUM_ADVERSARY}.npy",
    #    user_designations,
    #)
    return user_designations

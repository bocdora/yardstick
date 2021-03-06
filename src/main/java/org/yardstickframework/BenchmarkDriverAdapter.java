/*
 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package org.yardstickframework;

/**
 * Convenient adapter for benchmark driver implementations.
 */
public abstract class BenchmarkDriverAdapter implements BenchmarkDriver {
    /** Benchmark configuration. */
    protected BenchmarkConfiguration cfg;

    /** {@inheritDoc} */
    @Override public void setUp(BenchmarkConfiguration cfg) throws Exception {
        this.cfg = cfg;
    }

    /** {@inheritDoc} */
    @Override public void tearDown() throws Exception {
        // No-op.
    }

    /** {@inheritDoc} */
    @Override public String description() {
        String desc = BenchmarkUtils.description(cfg, this);

        return desc.isEmpty() ? getClass().getSimpleName() + cfg.defaultDescription() : desc;
    }

    /** {@inheritDoc} */
    @Override public String usage() {
        return BenchmarkUtils.usage(null);
    }

    /** {@inheritDoc} */
    @Override public void onWarmupFinished() {
        // No-op.
    }
}

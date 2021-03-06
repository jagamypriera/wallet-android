package com.mycelium.wallet.activity.modern.model.accounts

import android.annotation.SuppressLint
import android.arch.lifecycle.LiveData
import android.os.AsyncTask
import com.mycelium.wallet.AccountManager
import com.mycelium.wallet.MbwManager
import com.mycelium.wallet.R
import com.mycelium.wallet.Utils
import com.mycelium.wallet.activity.modern.model.accounts.AccountListItem.Type.GROUP_ARCHIVED_TITLE_TYPE
import com.mycelium.wallet.activity.modern.model.accounts.AccountListItem.Type.GROUP_TITLE_TYPE
import com.mycelium.wallet.colu.ColuAccount
import com.mycelium.wallet.event.AccountListChanged
import com.mycelium.wapi.wallet.WalletAccount
import com.squareup.otto.Subscribe
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * This class is intended to monitor current accounts and must post changes as soon as accounts list was updated.
 * @see AccountsViewLiveData.getValue retuns list of current accounts, with filtered hidden ones.
 */
class AccountsViewLiveData(private val mbwManager: MbwManager) : LiveData<List<AccountsGroupModel>>() {
    // List of all currently available accounts
    private var accountsList = Collections.emptyList<AccountsGroupModel>()
    private val executionService: ExecutorService

    init {
        value = Collections.emptyList()
        executionService = Executors.newCachedThreadPool()
        updateData()
    }

    override fun onActive() {
        MbwManager.getEventBus().register(this)
    }

    override fun onInactive() {
        MbwManager.getEventBus().unregister(this)
    }

    @Subscribe
    fun onAccountsListChanged(event: AccountListChanged) {
        updateData()
    }

    /**
     * Leak might not occur, as only application context passed and whole class don't contains any Activity related contexts
     */
    @SuppressLint("StaticFieldLeak")
    private inner class DataUpdateAsyncTask : AsyncTask<Void, List<AccountsGroupModel>, List<AccountsGroupModel>>() {
        override fun doInBackground(vararg voids: Void): List<AccountsGroupModel> {
            val am = AccountManager
            val accountsList = mutableListOf(AccountsGroupModel(R.string.active_hd_accounts_name, GROUP_TITLE_TYPE,
                    bipAccountsToViewModel(sortAccounts(am.getBTCBip44Accounts().values))))
            val singleAddressList = accountsToViewModel(sortAccounts(am.getBTCSingleAddressAccounts().values))
            if (singleAddressList.isNotEmpty()) {
                accountsList.add(AccountsGroupModel((R.string.active_bitcoin_sa_group_name), GROUP_TITLE_TYPE,
                        singleAddressList))
            }
            if (value!!.isEmpty()) {
                publishProgress(accountsList)
            }

            val bchBipList = bipAccountsToViewModel(sortAccounts(am.getBCHBip44Accounts().values))
            if (bchBipList.isNotEmpty()) {
                accountsList.add(AccountsGroupModel(R.string.bitcoin_cash_hd, GROUP_TITLE_TYPE,
                        bchBipList))
            }
            val bchSAList = accountsToViewModel(sortAccounts(am.getBCHSingleAddressAccounts().values))
            if (bchSAList.isNotEmpty()) {
                accountsList.add(AccountsGroupModel(R.string.bitcoin_cash_sa, GROUP_TITLE_TYPE,
                        bchSAList))
            }

            val coluAccounts = ArrayList<WalletAccount>()
            for (walletAccount in am.getColuAccounts().values) {
                coluAccounts.add(walletAccount)
                coluAccounts.add((walletAccount as ColuAccount).linkedAccount)
            }
            if (coluAccounts.isNotEmpty()) {
                accountsList.add(AccountsGroupModel(R.string.digital_assets, GROUP_TITLE_TYPE,
                        accountsToViewModel(sortAccounts(coluAccounts))))
            }
            val accounts = sortAccounts(am.getActiveAccounts().values.asList())
            val other = ArrayList<WalletAccount>()
            for (account in accounts) {
                when (account.type) {
                    WalletAccount.Type.BTCSINGLEADDRESS, WalletAccount.Type.BTCBIP44,
                    WalletAccount.Type.BCHSINGLEADDRESS, WalletAccount.Type.BCHBIP44, WalletAccount.Type.COLU -> {
                    }
                    else -> other.add(account)
                }
            }
            if (other.isNotEmpty()) {
                accountsList.add(AccountsGroupModel(R.string.active_other_accounts_name, GROUP_TITLE_TYPE,
                        accountsToViewModel(sortAccounts(other))))
            }

            val archivedList = accountsToViewModel(sortAccounts(am.getArchivedAccounts().values))
            if (archivedList.isNotEmpty()) {
                accountsList.add(AccountsGroupModel(R.string.archive_name, GROUP_ARCHIVED_TITLE_TYPE,
                        archivedList))
            }
            if (accountsList == value) {
                cancel(true)
            }
            return accountsList
        }

        private fun accountsToViewModel(accounts: Collection<WalletAccount>) =
                accounts.map { AccountViewModel(it, mbwManager) }
        private fun bipAccountsToViewModel(accounts: Collection<WalletAccount>) =
                accounts.map { AccountViewModel(it, mbwManager) }

        private fun sortAccounts(accounts: Collection<WalletAccount>) =
                Utils.sortAccounts(accounts, mbwManager.metadataStorage)

        @SafeVarargs
        override fun onProgressUpdate(vararg values: List<AccountsGroupModel>) {
            super.onProgressUpdate(*values)
            accountsList = values[0]
            updateList()
        }

        override fun onPostExecute(result: List<AccountsGroupModel>) {
            accountsList = result
            updateList()
        }
    }

    private fun updateData() {
        DataUpdateAsyncTask().executeOnExecutor(executionService)
    }

    /**
     * This method is mainly intended to be able to quickly collapse groups.
     */
    private fun updateList() {
        if (value != accountsList) {
            value = accountsList
        }
    }
}
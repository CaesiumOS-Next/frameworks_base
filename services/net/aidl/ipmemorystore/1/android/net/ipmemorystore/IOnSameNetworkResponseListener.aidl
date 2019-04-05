package android.net.ipmemorystore;
interface IOnSameNetworkResponseListener {
  oneway void onSameNetworkResponse(in android.net.ipmemorystore.StatusParcelable status, in android.net.ipmemorystore.SameL3NetworkResponseParcelable response);
}
